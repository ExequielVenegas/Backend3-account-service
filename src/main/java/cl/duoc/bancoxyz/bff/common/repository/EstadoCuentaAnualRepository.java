package cl.duoc.bancoxyz.bff.common.repository;

import cl.duoc.bancoxyz.bff.common.model.EstadoCuentaAnual;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EstadoCuentaAnualRepository extends CrudRepository<EstadoCuentaAnual, Integer> {

    @Query("SELECT id, cuenta_id, fecha, tipo_transaccion, monto, descripcion, auditado " +
            "FROM estado_cuenta_anual WHERE cuenta_id = :cuentaId ORDER BY fecha DESC, id DESC")
    List<EstadoCuentaAnual> findByCuentaId(Integer cuentaId);
}
