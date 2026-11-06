package com.stockly.identity;

import com.stockly.companies.CompanyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CompanyAccessFilter extends OncePerRequestFilter {
    private final CompanyRepository companies;
    public CompanyAccessFilter(CompanyRepository companies) { this.companies=companies; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof StocklyPrincipal principal && principal.companyId()!=null) {
            var company=companies.findById(principal.companyId());
            if(company.isEmpty() || !company.get().status().equals("ACTIVE") || company.get().accessVersion()!=principal.accessVersion()) {
                if(req.getSession(false)!=null) req.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                res.setStatus(401); res.setContentType("application/json");
                res.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"El acceso de la empresa no está disponible.\",\"fieldErrors\":{},\"requestId\":\""+java.util.UUID.randomUUID()+"\"}");
                return;
            }
        }
        chain.doFilter(req,res);
    }
}
