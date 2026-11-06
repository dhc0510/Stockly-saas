package com.stockly.companies;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.io.IOException;
import java.util.*;

/** Independent of the business database. Recovery must exclude both prepared and committed deletions. */
@Component
public class DeletionJournal {
    private final String directory;
    public DeletionJournal(@Value("${stockly.deletion-journal-directory:}") String directory) { this.directory=directory; }
    public synchronized void prepare(String companyId) { write(companyId,"PREPARED"); }
    public synchronized void committed(String companyId) { write(companyId,"COMMITTED"); }
    public synchronized void rolledBack(String companyId) { write(companyId,"ROLLED_BACK"); }
    private void write(String companyId,String state) {
        if(directory.isBlank()) throw new CompanyFailure(503,"DELETION_UNAVAILABLE","La eliminación segura no está configurada.");
        try {
            var dir=Path.of(directory);
            if(!dir.isAbsolute()) throw new IOException("Absolute journal path required");
            Files.createDirectories(dir);
            var path=dir.resolve(companyId+".deleted");
            // A durable intent precedes deletion; recovery never guesses the outcome
            // of an interrupted transaction with an unresolved PREPARED marker.
            try(var file=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                file.write(ByteBuffer.wrap((companyId+"\n"+Instant.now()+"\n"+state+"\n").getBytes(StandardCharsets.UTF_8)));
                file.force(true);
            }
        } catch(IOException ex) { throw new CompanyFailure(503,"DELETION_UNAVAILABLE","No se pudo registrar la eliminación de forma segura."); }
    }
    public synchronized Set<String> blockedCompanyIds() {
        if(directory.isBlank()) throw new CompanyFailure(503,"DELETION_UNAVAILABLE","La eliminación segura no está configurada.");
        var blocked=new HashSet<String>();
        var dir=Path.of(directory);
        if(!Files.isDirectory(dir)) throw new CompanyFailure(503,"DELETION_UNAVAILABLE","No se encontró el registro independiente de eliminaciones.");
        try(var files=Files.list(dir)) {
            for(var file:files.filter(p -> p.getFileName().toString().endsWith(".deleted")).toList()) {
                var lines=Files.readAllLines(file,StandardCharsets.UTF_8);
                if(lines.size()!=3 || !file.getFileName().toString().equals(lines.getFirst()+".deleted"))
                    throw new IOException("Invalid journal entry");
                UUID.fromString(lines.getFirst()); Instant.parse(lines.get(1));
                if(lines.get(2).equals("COMMITTED")) blocked.add(lines.getFirst());
                else if(!lines.get(2).equals("ROLLED_BACK")) throw new IOException("Unresolved deletion intent");
            }
        } catch(Exception ex) { throw new CompanyFailure(503,"DELETION_UNAVAILABLE","El registro de eliminaciones requiere revisión antes de restaurar."); }
        return Set.copyOf(blocked);
    }
}
