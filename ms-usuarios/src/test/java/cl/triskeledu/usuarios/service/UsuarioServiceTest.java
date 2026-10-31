package cl.triskeledu.usuarios.service;

import cl.triskeledu.common.exception.DuplicateResourceException;
import cl.triskeledu.common.exception.EntityNotFoundException;
import cl.triskeledu.usuarios.dto.UsuarioRequest;
import cl.triskeledu.usuarios.dto.UsuarioResponse;
import cl.triskeledu.usuarios.mapper.UsuarioMapper;
import cl.triskeledu.usuarios.model.Usuario;
import cl.triskeledu.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de UsuarioService: CRUD, email único y activación de cuentas.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    // Dependencias simuladas
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioMapper usuarioMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    // Datos de prueba
    private Usuario usuario(long id, String email) {
        return Usuario.builder().id(id).nombre("Ana").apellido("Rojas").email(email).activo(true).build();
    }

    private UsuarioRequest request(String email) {
        UsuarioRequest request = new UsuarioRequest();
        request.setNombre("Ana");
        request.setApellido("Rojas");
        request.setEmail(email);
        request.setPassword("Secreta123");
        request.setRol("Cliente");
        return request;
    }

    // Consultas
    @Test
    void findAllDevuelveTodosLosUsuarios() {
        List<Usuario> usuarios = List.of(usuario(1, "a@x.cl"), usuario(2, "b@x.cl"));
        List<UsuarioResponse> respuestas = List.of(new UsuarioResponse(), new UsuarioResponse());
        when(usuarioRepository.findAll()).thenReturn(usuarios);
        when(usuarioMapper.toResponseList(usuarios)).thenReturn(respuestas);

        assertEquals(2, usuarioService.findAll().size());
    }

    @Test
    void findByIdDevuelveElUsuario() {
        Usuario usuario = usuario(1, "a@x.cl");
        UsuarioResponse respuesta = new UsuarioResponse();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioMapper.toResponse(usuario)).thenReturn(respuesta);

        assertSame(respuesta, usuarioService.findById(1L));
    }

    @Test
    void findByIdInexistenteLanzaEntityNotFound() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.findById(99L));
    }

    @Test
    void findByEmailDevuelveElUsuario() {
        Usuario usuario = usuario(1, "a@x.cl");
        UsuarioResponse respuesta = new UsuarioResponse();
        when(usuarioRepository.findByEmail("a@x.cl")).thenReturn(Optional.of(usuario));
        when(usuarioMapper.toResponse(usuario)).thenReturn(respuesta);

        assertSame(respuesta, usuarioService.findByEmail("a@x.cl"));
    }

    @Test
    void findByEmailInexistenteLanzaEntityNotFound() {
        when(usuarioRepository.findByEmail("no@x.cl")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.findByEmail("no@x.cl"));
    }

    // Creación
    @Test
    void createHasheaLaClaveYActivaElUsuario() {
        when(usuarioRepository.findByEmail("nuevo@x.cl")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secreta123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponse());

        usuarioService.create(request("nuevo@x.cl"));

        verify(usuarioMapper).updateEntity(any(UsuarioRequest.class), any(Usuario.class));
        verify(usuarioRepository).save(argThat(u -> "hash".equals(u.getPassword()) && Boolean.TRUE.equals(u.getActivo())));
    }

    @Test
    void createConEmailDuplicadoLanzaDuplicateResource() {
        when(usuarioRepository.findByEmail("a@x.cl")).thenReturn(Optional.of(usuario(1, "a@x.cl")));

        assertThrows(DuplicateResourceException.class, () -> usuarioService.create(request("a@x.cl")));
        verify(usuarioRepository, never()).save(any());
    }

    // Actualización
    @Test
    void updateConMismoEmailNoValidaDuplicado() {
        Usuario usuario = usuario(1, "a@x.cl");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);
        when(usuarioMapper.toResponse(usuario)).thenReturn(new UsuarioResponse());

        usuarioService.update(1L, request("A@X.CL"));

        verify(usuarioRepository, never()).findByEmail(any());
        assertEquals("hash", usuario.getPassword());
    }

    @Test
    void updateConEmailNuevoValidaQueSeaUnico() {
        Usuario usuario = usuario(1, "a@x.cl");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmail("b@x.cl")).thenReturn(Optional.of(usuario(2, "b@x.cl")));

        assertThrows(DuplicateResourceException.class, () -> usuarioService.update(1L, request("b@x.cl")));
    }

    @Test
    void updateDeUsuarioInexistenteLanzaEntityNotFound() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.update(9L, request("a@x.cl")));
    }

    // Eliminación
    @Test
    void deleteByIdEliminaElUsuario() {
        Usuario usuario = usuario(1, "a@x.cl");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.deleteById(1L);

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    void deleteByIdInexistenteLanzaEntityNotFound() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.deleteById(9L));
        verify(usuarioRepository, never()).delete(any());
    }

    // Activación y desactivación
    @Test
    void activarMarcaElUsuarioComoActivo() {
        Usuario usuario = usuario(1, "a@x.cl");
        usuario.setActivo(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        usuarioService.activar(1L);

        assertTrue(usuario.getActivo());
    }

    @Test
    void desactivarMarcaElUsuarioComoInactivo() {
        Usuario usuario = usuario(1, "a@x.cl");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        usuarioService.desactivar(1L);

        assertFalse(usuario.getActivo());
    }
}
