package cl.triskeledu.recursos.service;

import cl.triskeledu.common.exception.DuplicateResourceException;
import cl.triskeledu.common.exception.EntityNotFoundException;
import cl.triskeledu.common.exception.ReferentialIntegrityException;
import cl.triskeledu.recursos.client.CatalogoClient;
import cl.triskeledu.recursos.dto.RecursoFisicoRequest;
import cl.triskeledu.recursos.dto.RecursoFisicoResponse;
import cl.triskeledu.recursos.mapper.RecursoFisicoMapper;
import cl.triskeledu.recursos.model.HistorialEventoRecursoFisico;
import cl.triskeledu.recursos.model.LibroProyeccion;
import cl.triskeledu.recursos.model.MantenimientoRecurso;
import cl.triskeledu.recursos.model.RecursoFisico;
import cl.triskeledu.recursos.repository.LibroProyeccionRepository;
import cl.triskeledu.recursos.repository.RecursoFisicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de RecursoFisicoService: consultas, SKU único,
 * validación del ISBN contra el catálogo e integridad referencial.
 */
@ExtendWith(MockitoExtension.class)
class RecursoFisicoServiceTest {

    private static final String ISBN = "9789563251234";

    // Dependencias simuladas
    @Mock
    private RecursoFisicoRepository recursoRepository;
    @Mock
    private LibroProyeccionRepository libroProyeccionRepository;
    @Mock
    private RecursoFisicoMapper recursoMapper;
    @Mock
    private CatalogoClient catalogoClient;

    @InjectMocks
    private RecursoFisicoService recursoService;

    // Datos de prueba
    private RecursoFisico recurso(String sku) {
        return RecursoFisico.builder().id(1L).sku(sku).tipoRecurso("Libro").disponible(true).build();
    }

    private RecursoFisicoRequest request(String sku, String tipo, String isbn) {
        RecursoFisicoRequest request = new RecursoFisicoRequest();
        request.setSku(sku);
        request.setTipoRecurso(tipo);
        request.setIsbn(isbn);
        request.setEstadoFisico("Excelente");
        return request;
    }

    private LibroProyeccion libro() {
        return LibroProyeccion.builder().isbn(ISBN).titulo("Rayuela").build();
    }

    // Consultas
    @Test
    void findAllDevuelveTodosLosRecursos() {
        List<RecursoFisico> recursos = List.of(recurso("SKU-1"));
        when(recursoRepository.findAll()).thenReturn(recursos);
        when(recursoMapper.toResponseList(recursos)).thenReturn(List.of(new RecursoFisicoResponse()));

        assertEquals(1, recursoService.findAll().size());
    }

    @Test
    void findByIdDevuelveElRecurso() {
        RecursoFisico recurso = recurso("SKU-1");
        RecursoFisicoResponse respuesta = new RecursoFisicoResponse();
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso));
        when(recursoMapper.toResponse(recurso)).thenReturn(respuesta);

        assertSame(respuesta, recursoService.findById(1L));
    }

    @Test
    void findByIdInexistenteLanzaEntityNotFound() {
        when(recursoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.findById(9L));
    }

    @Test
    void findBySkuDevuelveElRecurso() {
        RecursoFisico recurso = recurso("SKU-1");
        RecursoFisicoResponse respuesta = new RecursoFisicoResponse();
        when(recursoRepository.findBySku("SKU-1")).thenReturn(Optional.of(recurso));
        when(recursoMapper.toResponse(recurso)).thenReturn(respuesta);

        assertSame(respuesta, recursoService.findBySku("SKU-1"));
    }

    @Test
    void findBySkuInexistenteLanzaEntityNotFound() {
        when(recursoRepository.findBySku("NO")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.findBySku("NO"));
    }

    @Test
    void findByDisponibleFiltraPorDisponibilidad() {
        List<RecursoFisico> recursos = List.of(recurso("SKU-1"));
        when(recursoRepository.findByDisponible(true)).thenReturn(recursos);
        when(recursoMapper.toResponseList(recursos)).thenReturn(List.of(new RecursoFisicoResponse()));

        assertEquals(1, recursoService.findByDisponible(true).size());
    }

    @Test
    void findByTipoRecursoFiltraPorTipo() {
        List<RecursoFisico> recursos = List.of(recurso("SKU-1"));
        when(recursoRepository.findByTipoRecurso("Libro")).thenReturn(recursos);
        when(recursoMapper.toResponseList(recursos)).thenReturn(List.of(new RecursoFisicoResponse()));

        assertEquals(1, recursoService.findByTipoRecurso("Libro").size());
    }

    @Test
    void existsByIsbnConsultaElRepositorio() {
        when(recursoRepository.existsByLibroIsbn(ISBN)).thenReturn(true);

        assertTrue(recursoService.existsByIsbn(ISBN));
    }

    // Creación
    @Test
    void createAsociaElLibroYQuedaDisponible() {
        when(recursoRepository.findBySku("SKU-1")).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(ISBN)).thenReturn(true);
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.of(libro()));
        when(recursoRepository.save(any(RecursoFisico.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recursoMapper.toResponse(any(RecursoFisico.class))).thenReturn(new RecursoFisicoResponse());

        recursoService.create(request("SKU-1", "Libro", ISBN));

        verify(recursoRepository).save(argThat(r ->
                "Libro".equals(r.getTipoRecurso())
                        && Boolean.TRUE.equals(r.getDisponible())
                        && r.getLibro() != null
                        && ISBN.equals(r.getLibro().getIsbn())));
    }

    @Test
    void createConSkuDuplicadoLanzaDuplicateResource() {
        when(recursoRepository.findBySku("SKU-1")).thenReturn(Optional.of(recurso("SKU-1")));

        assertThrows(DuplicateResourceException.class,
                () -> recursoService.create(request("SKU-1", "Libro", ISBN)));
        verifyNoInteractions(catalogoClient);
    }

    @Test
    void createConIsbnQueNoExisteEnCatalogoFalla() {
        when(recursoRepository.findBySku("SKU-1")).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(ISBN)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> recursoService.create(request("SKU-1", "Libro", ISBN)));
        verify(recursoRepository, never()).save(any());
    }

    @Test
    void createSinProyeccionLocalDelLibroFalla() {
        when(recursoRepository.findBySku("SKU-1")).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(ISBN)).thenReturn(true);
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> recursoService.create(request("SKU-1", "Libro", ISBN)));
    }

    // Actualización
    @Test
    void updateDeLibroResuelveLaProyeccion() {
        RecursoFisico recurso = recurso("SKU-1");
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso));
        when(libroProyeccionRepository.findById(ISBN)).thenReturn(Optional.of(libro()));
        when(recursoRepository.save(recurso)).thenReturn(recurso);

        recursoService.update(1L, request("sku-1", "Libro", ISBN));

        assertEquals(ISBN, recurso.getLibro().getIsbn());
        verify(recursoRepository, never()).findBySku(any());
    }

    @Test
    void updateDeOtroTipoQuitaElLibro() {
        RecursoFisico recurso = recurso("SKU-1");
        recurso.setLibro(libro());
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso));
        when(recursoRepository.findBySku("SKU-2")).thenReturn(Optional.empty());
        when(recursoRepository.save(recurso)).thenReturn(recurso);

        recursoService.update(1L, request("SKU-2", "Notebook", null));

        assertNull(recurso.getLibro());
    }

    @Test
    void updateConSkuDeOtroRecursoLanzaDuplicateResource() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso("SKU-1")));
        when(recursoRepository.findBySku("SKU-2")).thenReturn(Optional.of(recurso("SKU-2")));

        assertThrows(DuplicateResourceException.class,
                () -> recursoService.update(1L, request("SKU-2", "Libro", ISBN)));
    }

    @Test
    void updateDeLibroSinIsbnLanzaIllegalArgument() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso("SKU-1")));

        assertThrows(IllegalArgumentException.class,
                () -> recursoService.update(1L, request("SKU-1", "Libro", " ")));
    }

    @Test
    void updateDeLibroConIsbnNuloLanzaIllegalArgument() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso("SKU-1")));

        assertThrows(IllegalArgumentException.class,
                () -> recursoService.update(1L, request("SKU-1", "Libro", null)));
    }

    @Test
    void updateDeLibroConIsbnSinProyeccionLanzaEntityNotFound() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso("SKU-1")));
        when(libroProyeccionRepository.findById(ISBN)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> recursoService.update(1L, request("SKU-1", "Libro", ISBN)));
    }

    // Eliminación e integridad referencial
    @Test
    void deleteByIdSinDependenciasElimina() {
        RecursoFisico recurso = recurso("SKU-1");
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso));

        recursoService.deleteById(1L);

        verify(recursoRepository).delete(recurso);
    }

    @Test
    void deleteByIdConHistorialYMantenimientosLanzaReferentialIntegrity() {
        RecursoFisico recurso = recurso("SKU-1");
        recurso.setHistorial(new ArrayList<>(List.of(new HistorialEventoRecursoFisico())));
        recurso.setMantenimientos(new ArrayList<>(List.of(new MantenimientoRecurso())));
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recurso));

        ReferentialIntegrityException ex = assertThrows(ReferentialIntegrityException.class,
                () -> recursoService.deleteById(1L));

        assertTrue(ex.getMessage().contains("Historial"));
        verify(recursoRepository, never()).delete(any());
    }
}
