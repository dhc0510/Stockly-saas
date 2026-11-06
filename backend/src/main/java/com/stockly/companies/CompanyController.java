package com.stockly.companies;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.net.URI;
import static com.stockly.companies.CompanyContracts.*;

@RestController @RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service=service; }
    @GetMapping CompanyPage list(@RequestParam(required=false) String search,@RequestParam(required=false) String status,
        @RequestParam(required=false) LocalDate registeredFrom,@RequestParam(required=false) LocalDate registeredTo,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(search,status,registeredFrom,registeredTo,page,size);
    }
    @GetMapping("/{id}") CompanyView get(@PathVariable String id) { return service.get(id); }
    @PostMapping ResponseEntity<CompanyView> create(@RequestBody CreateCompany request) {
        var company=service.create(request);
        return ResponseEntity.created(URI.create("/api/companies/"+company.id())).body(company);
    }
    @PatchMapping("/{id}") CompanyView update(@PathVariable String id,@RequestBody UpdateCompany request) { return service.update(id,request); }
    @PostMapping("/{id}/suspend") CompanyView suspend(@PathVariable String id,@RequestBody StateChange request) { return service.state(id,request,"SUSPENDED"); }
    @PostMapping("/{id}/reactivate") CompanyView reactivate(@PathVariable String id,@RequestBody StateChange request) { return service.state(id,request,"ACTIVE"); }
    @PostMapping("/{id}/delete") ResponseEntity<Void> delete(@PathVariable String id,@RequestBody DeleteCompany request) {
        service.delete(id,request); return ResponseEntity.noContent().build();
    }
}
