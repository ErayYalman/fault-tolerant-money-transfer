from __future__ import annotations

import os
from typing import Any

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import PyJWKClient
from jwt.exceptions import (
    InvalidTokenError,
    PyJWKClientConnectionError,
    PyJWKClientError,
)


KEYCLOAK_ISSUER = os.getenv(
    "KEYCLOAK_ISSUER",
    "http://localhost:8180/realms/money-transfer",
)

KEYCLOAK_JWKS_URL = os.getenv(
    "KEYCLOAK_JWKS_URL",
    "http://localhost:8180/realms/money-transfer/"
    "protocol/openid-connect/certs",
)

JWT_ALGORITHM = os.getenv("KEYCLOAK_JWT_ALGORITHM", "RS256")

ML_AUDIENCE = os.getenv("ML_OIDC_AUDIENCE", "ml-service")

AUTHORIZED_PARTY = os.getenv(
    "ML_OIDC_AUTHORIZED_PARTY",
    "reconciliation-service",
)

REQUIRED_ROLE = os.getenv(
    "ML_OIDC_REQUIRED_ROLE",
    "anomaly-score",
)


# JWKS response is cached for 5 minutes by default.
# Network timeout is deliberately kept short.
jwks_client = PyJWKClient(
    KEYCLOAK_JWKS_URL,
    cache_jwk_set=True,
    lifespan=300,
    timeout=5,
)

bearer_scheme = HTTPBearer(auto_error=False)


def unauthorized(detail: str = "Invalid or expired access token") -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


def verify_access_token(token: str) -> dict[str, Any]:
    try:
        signing_key = jwks_client.get_signing_key_from_jwt(token)

        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=[JWT_ALGORITHM],
            issuer=KEYCLOAK_ISSUER,
            audience=ML_AUDIENCE,
            options={
                "require": ["exp", "iat", "iss", "aud"],
            },
        )

    except PyJWKClientConnectionError as exception:
        # Fail closed if Keycloak's signing keys cannot be reached.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Identity provider signing keys are unavailable",
        ) from exception

    except (InvalidTokenError, PyJWKClientError) as exception:
        raise unauthorized() from exception

    return claims


def require_anomaly_scoring_access(
    credentials: HTTPAuthorizationCredentials | None = Depends(
        bearer_scheme
    ),
) -> dict[str, Any]:

    if credentials is None:
        raise unauthorized("Bearer token is required")

    claims = verify_access_token(credentials.credentials)

    # The token must have been issued to the expected machine client.
    if claims.get("azp") != AUTHORIZED_PARTY:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Client is not authorized to access anomaly scoring",
        )

    # Check the client role assigned to this resource API.
    resource_access = claims.get("resource_access", {})
    ml_access = (
        resource_access.get(ML_AUDIENCE, {})
        if isinstance(resource_access, dict)
        else {}
    )

    roles = (
        ml_access.get("roles", [])
        if isinstance(ml_access, dict)
        else []
    )

    if REQUIRED_ROLE not in roles:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Required API role is missing",
        )

    return claims