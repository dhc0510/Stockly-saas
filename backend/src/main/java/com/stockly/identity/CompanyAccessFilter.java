package com.stockly.identity;

import com.stockly.companies.CompanyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CompanyAccessFilter extends OncePerRequestFilter {
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final com.stockly.subscriptions.SubscriptionGuard subscriptions;
    public CompanyAccessFilter(CompanyRepository companies, AccountRepository accounts, com.stockly.subscriptions.SubscriptionGuard subscriptions) {
        this.companies=companies; this.accounts=accounts; this.subscriptions=subscriptions;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof StocklyPrincipal principal) {
            var company=principal.companyId()==null ? null : companies.findById(principal.companyId()).orElse(null);
            var account=accounts.findById(principal.accountId()).orElse(null);
            boolean companyInvalid=principal.companyId()!=null && (company==null || !company.status().equals("ACTIVE") || company.accessVersion()!=principal.accessVersion()
                || subscriptions.suspended(principal.companyId())); // STK-018: suspendida por falta de pago
            boolean accountInvalid=account==null || !account.active() || account.accessVersion()!=principal.accountAccessVersion();
            if(companyInvalid || accountInvalid) {
                revoke(req, res); return;
            }
            if(company!=null) {
                var session=req.getSession(false);
                if(session!=null) {
                    long now=System.currentTimeMillis();
                    Object lastValue=session.getAttribute("STOCKLY_LAST_ACTIVITY");
                    Long timeout=company.idleTimeoutMinutes();
                    if(timeout==null) req.setAttribute("STOCKLY_PERSISTENT_SESSION",Boolean.TRUE);
                    if(timeout!=null && lastValue instanceof Long last && now>last && (now-last)/60_000L > timeout) {
                        revoke(req,res); return;
                    }
                    session.setMaxInactiveInterval(timeout==null || timeout>Integer.MAX_VALUE/60 ? -1 : timeout.intValue()*60);
                    session.setAttribute("STOCKLY_LAST_ACTIVITY",now);
                }
            }
        }
        chain.doFilter(req,res);
    }
    private static void revoke(HttpServletRequest req,HttpServletResponse res) throws IOException {
        if(req.getSession(false)!=null) req.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        res.setStatus(401); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"El acceso ya no está disponible. Inicia sesión de nuevo.\",\"fieldErrors\":{},\"requestId\":\""+java.util.UUID.randomUUID()+"\"}");
    }
}
