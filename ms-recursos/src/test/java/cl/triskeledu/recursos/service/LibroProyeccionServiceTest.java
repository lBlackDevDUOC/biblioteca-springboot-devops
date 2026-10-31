package cl.triskeledu.recursos.service;

import cl.triskeledu.common.exception.ReferentialIntegrityException;
import cl.triskeledu.recursos.dto.LibroProyeccionResponse;
import cl.triskeledu.recursos.mapper.LibroProyeccionMapper;
import cl.triskeledu.recursos.model.LibroProyeccion;
import cl.triskeledu.recursos.repository.LibroProyeccionRepository;
import cl.triskeledu.recursos.repository.RecursoFisicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de LibroProyeccionService: sincronización de la copia local
 * de libros que llega desde ms-catalogo.
 */
@ExtendWith(MockitoExtension.class)
class LibroProyeccionServiceTest {

    private static final String ISBN = "9789563251234";

    // Dependencias simuladas
    @Mock
    private LibroProyeccionRepository libroProyeccionRepository;
    @Mock
    private RecursoFisicoRepository recursoFisicoRepository;
    @Mock
    private LibroProyeccionMapper libroProyeccionMapper;

    @InjectMocks
    private LibroProyeccionService libroProyeccionService;

    @Test
    void findAllDevuelveLasProyecciones() {
        List<LibroProyeccion> libros = List.of(new LibroProyeccion(ISBN, "Rayuela"));
        when(libroProyeccionRepository.findAll()).thenReturn(libros);
        when(libroProyeccionMapper.toResponseList(libros)).thenReturn(List.of(new LibroProyeccionResponse()));

        assertEquals(1, libroProyeccionService.findAll().size());
    }

    // Sincronización
    @Test
    void saveCreaLaProyeccionSiNoExiste() {
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.empty());

        libroProyeccionService.save(ISBN, "Rayuela");

        verify(libroProyeccionRepository).save(argThat(l -> ISBN.equals(l.getIsbn()) && "Rayuela".equals(l.getTitulo())));
    }

    @Test
    void saveActualizaElTituloSiYaExiste() {
        LibroProyeccion existente = new LibroProyeccion(ISBN, "Título antiguo");
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.of(existente));

        libroProyeccionService.save(ISBN, "Rayuela");

        assertEquals("Rayuela", existente.getTitulo());
        verify(libroProyeccionRepository).save(existente);
    }

    // Eliminación e integridad referencial
    @Test
    void deleteByIsbnEliminaSiNoTieneRecursos() {
        LibroProyeccion existente = new LibroProyeccion(ISBN, "Rayuela");
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.of(existente));
        when(recursoFisicoRepository.existsByLibroIsbn(ISBN)).thenReturn(false);

        libroProyeccionService.deleteByIsbn(ISBN);

        verify(libroProyeccionRepository).delete(existente);
    }

    @Test
    void deleteByIsbnConRecursosAsociadosLanzaReferentialIntegrity() {
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.of(new LibroProyeccion(ISBN, "Rayuela")));
        when(recursoFisicoRepository.existsByLibroIsbn(ISBN)).thenReturn(true);

        assertThrows(ReferentialIntegrityException.class, () -> libroProyeccionService.deleteByIsbn(ISBN));
        verify(libroProyeccionRepository, never()).delete(any());
    }

    @Test
    void deleteByIsbnInexistenteNoHaceNada() {
        when(libroProyeccionRepository.findByIsbn(ISBN)).thenReturn(Optional.empty());

        libroProyeccionService.deleteByIsbn(ISBN);

        verify(libroProyeccionRepository, never()).delete(any());
        verifyNoInteractions(recursoFisicoRepository);
    }
}
