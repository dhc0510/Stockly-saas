package com.stockly.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SessionConfiguration {
    @Bean
    CookieSerializer stocklyCookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("STOCKLY_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(-1);
        serializer.setRememberMeRequestAttribute("STOCKLY_PERSISTENT_SESSION");
        return serializer;
    }
}
