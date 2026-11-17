package com.universidad.msvsectoerasiento;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.repository.*;
import com.universidad.msvsectoerasiento.service.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ServiceValidationTests {
    @Test
    void sectorValidaSinPasarPorControlador() {
        var repository = mock(SectorRepository.class);
        var service = new SectorService(repository, mock(AsientoRepository.class), mock(SectorEventoRepository.class));
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var factory = new ProxyFactory(service);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new MethodValidationInterceptor(validators.getValidator()));
            var proxy = (SectorService) factory.getProxy();
            assertThatThrownBy(() -> proxy.crear(new SectorRequest())).isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> proxy.crear(null)).isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> proxy.obtener(-1L)).isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(repository);
        }
    }

    @Test
    void asientoValidaSinPasarPorControlador() {
        var repository = mock(AsientoRepository.class);
        var service = new AsientoService(repository, mock(SectorRepository.class),
                mock(SectorEventoRepository.class), mock(AsientoEventoRepository.class));
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var factory = new ProxyFactory(service);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new MethodValidationInterceptor(validators.getValidator()));
            var proxy = (AsientoService) factory.getProxy();
            assertThatThrownBy(() -> proxy.crear(new AsientoRequest())).isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> proxy.actualizar(1L, null)).isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> proxy.eliminar(0L)).isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(repository);
        }
    }
}
