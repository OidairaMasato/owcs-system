package jp.oidaira.owcs.repo;

import jp.oidaira.owcs.domain.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateRepository extends JpaRepository<SyncState, String> {
}
