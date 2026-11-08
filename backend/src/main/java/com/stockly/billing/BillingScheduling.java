package com.stockly.billing;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el envío periódico de comprobantes (FiscalDispatcher). */
@Configuration
@EnableScheduling
class BillingScheduling {}
