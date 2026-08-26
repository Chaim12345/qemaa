package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VmDao {
  @Query("SELECT * FROM virtual_machines ORDER BY id DESC")
  fun getAllVms(): Flow<List<VirtualMachineEntity>>

  @Query("SELECT * FROM virtual_machines WHERE id = :id")
  fun getVmByIdFlow(id: Long): Flow<VirtualMachineEntity?>

  @Query("SELECT * FROM virtual_machines WHERE id = :id")
  suspend fun getVmById(id: Long): VirtualMachineEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVm(vm: VirtualMachineEntity): Long

  @Update
  suspend fun updateVm(vm: VirtualMachineEntity)

  @Query("DELETE FROM virtual_machines WHERE id = :id")
  suspend fun deleteVm(id: Long)

  @Query("UPDATE virtual_machines SET status = :status WHERE id = :id")
  suspend fun updateVmStatus(id: Long, status: String)

  @Query("UPDATE virtual_machines SET uptimeSeconds = uptimeSeconds + :seconds WHERE id = :id")
  suspend fun incrementUptime(id: Long, seconds: Long)

  // Snapshots
  @Query("SELECT * FROM vm_snapshots WHERE vmId = :vmId ORDER BY timestamp DESC")
  fun getSnapshotsForVm(vmId: Long): Flow<List<VmSnapshotEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnapshot(snapshot: VmSnapshotEntity): Long

  @Query("DELETE FROM vm_snapshots WHERE id = :snapshotId")
  suspend fun deleteSnapshot(snapshotId: Long)
}
