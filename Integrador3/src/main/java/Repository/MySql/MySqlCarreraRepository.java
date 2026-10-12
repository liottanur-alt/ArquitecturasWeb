package Repository.MySql;

import Entities.Carera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MySqlCarreraRepository extends JpaRepository<Carrrera, Integer>{


}
