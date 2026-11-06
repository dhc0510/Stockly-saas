package com.stockly.companies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DeletionJournalTest {
    @TempDir Path directory;
    @Test void unconfiguredJournalBlocksDeletionAndRecovery() {
        var journal=new DeletionJournal("");
        assertThatThrownBy(() -> journal.prepare(UUID.randomUUID().toString())).isInstanceOf(CompanyFailure.class);
        assertThatThrownBy(journal::blockedCompanyIds).isInstanceOf(CompanyFailure.class);
    }
    @Test void corruptMarkerStopsRecoveryInsteadOfIgnoringADeletion() throws Exception {
        Files.writeString(directory.resolve("bad.deleted"),"incomplete");
        assertThatThrownBy(() -> new DeletionJournal(directory.toString()).blockedCompanyIds()).isInstanceOf(CompanyFailure.class);
    }
    @Test void unresolvedIntentBlocksRecoveryAndRolledBackIntentDoesNotDeleteData() {
        var journal=new DeletionJournal(directory.toString());
        String id=UUID.randomUUID().toString();
        journal.prepare(id);
        assertThatThrownBy(journal::blockedCompanyIds).isInstanceOf(CompanyFailure.class);
        journal.rolledBack(id);
        assertThat(journal.blockedCompanyIds()).isEmpty();
        journal.prepare(id); journal.committed(id);
        assertThat(journal.blockedCompanyIds()).containsExactly(id);
    }
}
