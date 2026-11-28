
# Keycloak Password Blacklist

The `10k-most-common.txt` file is based on the SecLists
10k most common passwords wordlist.

Source:
https://github.com/danielmiessler/SecLists/blob/master/Passwords/Common-Credentials/10k-most-common.txt

The upstream MIT license is included in `LICENSE-SecLists.txt`.

Application-specific password entries are appended to the list
to reject passwords based on the Money Transfer Platform context.

Keycloak reads this file during password policy validation.
The file is mounted read-only into the Keycloak container.
