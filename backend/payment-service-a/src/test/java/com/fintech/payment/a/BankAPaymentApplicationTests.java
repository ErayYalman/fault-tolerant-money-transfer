package com.fintech.payment.a;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "app.kafka.enabled=false", "app.outbox.relay.enabled=false" })
class BankAPaymentApplicationTests {

	@Test
	void contextLoads() {
	}

}
