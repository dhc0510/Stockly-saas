package com.stockly.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.stockly.identity.AuditContracts.*;

@Service
public class AuditEventService {
    private static final Map<String,String> LABELS=Map.of(
        "USER_CREATED","Trabajador creado",
        "TEMP_PASSWORD_ISSUED","Contraseña temporal emitida",
        "USER_DEACTIVATED","Trabajador desactivado",
        "USER_ROLE_ASSIGNED","Rol de trabajador actualizado",
        "ROLE_CREATED","Rol creado",
        "ROLE_UPDATED","Rol o permisos actualizados",
        "ROLE_DELETED","Rol eliminado",
        "SESSION_POLICY_UPDATED","Política de sesión actualizada"
    );
    private final JdbcTemplate jdbc;
    public AuditEventService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public void record(String action,String targetType,String targetId) {
        var principal=principal(); Instant now=Instant.now();
        jdbc.update("INSERT INTO identity_audit_events(id,company_id,actor_id,action_key,target_type,target_id,occurred_at) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),principal.companyId(),principal.accountId(),action,targetType,targetId,now);
    }

    @Transactional(readOnly=true)
    public AuditPage list(String action,int page,int size) {
        var principal=principal();
        if(!"COMPANY_ADMIN".equals(principal.role())) throw new IdentityFailure(403,"FORBIDDEN","Solo el administrador de empresa puede consultar esta actividad.");
        if(page<0 || size<1 || size>100) throw new IdentityFailure(400,"VALIDATION","Revisa la paginación.");
        if(action!=null && !LABELS.containsKey(action)) throw new IdentityFailure(400,"VALIDATION","El filtro de acción no es válido.");
        String from=" FROM identity_audit_events e LEFT JOIN users actor ON actor.id=e.actor_id LEFT JOIN users target_user ON e.target_type='USER' AND target_user.id=e.target_id "
            +"LEFT JOIN company_roles target_role ON e.target_type='ROLE' AND target_role.id=e.target_id ";
        String where=" WHERE e.company_id=?"+(action==null?"":" AND e.action_key=?");
        Object[] params=action==null?new Object[]{principal.companyId()}:new Object[]{principal.companyId(),action};
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,params);
        String sql="SELECT e.id,e.actor_id,actor.full_name,e.action_key,e.target_type,e.target_id,"
            +"COALESCE(target_user.full_name,target_role.name,CASE WHEN e.target_type='SESSION_POLICY' THEN 'Política de sesión' ELSE 'Registro eliminado' END),e.occurred_at"
            +from+where
            +"ORDER BY e.occurred_at DESC,e.id DESC LIMIT ? OFFSET ?";
        Object[] queryParams=Arrays.copyOf(params,params.length+2); queryParams[params.length]=size; queryParams[params.length+1]=(long)page*size;
        List<AuditItem> items=jdbc.query(sql,(rs,row)->new AuditItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
            LABELS.get(rs.getString(4)),rs.getString(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),queryParams);
        return new AuditPage(items,total==null?0:total,page,size);
    }
    private static StocklyPrincipal principal() {
        Object value=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(value instanceof StocklyPrincipal p) || p.companyId()==null) throw new IdentityFailure(403,"COMPANY_REQUIRED","La operación requiere una cuenta de empresa.");
        return p;
    }
}
