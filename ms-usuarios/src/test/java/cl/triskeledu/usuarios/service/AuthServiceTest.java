package cl.triskeledu.usuarios.service;

import cl.triskeledu.common.security.JwtProperties;
import cl.triskeledu.common.security.JwtTokenProvider;
import cl.triskeledu.usuarios.dto.LoginRequest;
import cl.triskeledu.usuarios.dto.LoginResponse;
import cl.triskeledu.usuarios.dto.RegisterRequest;
import cl.triskeledu.usuarios.dto.UsuarioResponse;
import cl.triskeledu.usuarios.mapper.UsuarioMapper;
import cl.triskeledu.usuarios.model.CredencialUsuario;
import cl.triskeledu.usuarios.model.Usuario;
import cl.triskeledu.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de AuthService: login, bloqueo por intentos fallidos y registro.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@biblioteca.cl";
    private static final String CLAVE = "Secreta123";
    private static final String HASH = "$2a$10$hash";

    // Dependencias simuladas
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioMapper usuarioMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    // Datos de prueba
    private Usuario usuarioActivo(CredencialUsuario credencial) {
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nombre("Ana")
                .apellido("Rojas")
                .email(EMAIL)
                .password(HASH)
                .rol("Cliente")
                .activo(true)
                .credencial(credencial)
                .build();
        if (credencial != null) {
            credencial.setUsuario(usuario);
        }
        return usuario;
    }

    private CredencialUsuario credencial(int intentos, boolean bloqueado) {
        return CredencialUsuario.builder().intentosFallidos(intentos).bloqueado(bloqueado).build();
    }

    private LoginRequest login(String clave) {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(clave);
        return request;
    }

    // Login exitoso
    @Test
    void loginExitosoGeneraTokenYReiniciaIntentos() {
        CredencialUsuario credencial = credencial(3, false);
        Usuario usuario = usuarioActivo(credencial);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(CLAVE, HASH)).thenReturn(true);
        when(jwtTokenProvider.generarToken(EMAIL, "Cliente", "Ana Rojas")).thenReturn("token-jwt");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);

        LoginResponse response = authService.login(login(CLAVE));

        assertEquals("token-jwt", response.getToken());
        assertEquals("Ana Rojas", response.getNombre());
        assertEquals("Cliente", response.getRol());
        assertEquals(3600000L, response.getExpiresIn());
        assertEquals(0, credencial.getIntentosFallidos());
        assertNotNull(credencial.getUltimoAcceso());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void loginExitosoSinCredencialCreaUnaNueva() {
        Usuario usuario = usuarioActivo(null);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(CLAVE, HASH)).thenReturn(true);
        when(jwtTokenProvider.generarToken(any(), any(), any())).thenReturn("token-jwt");

        authService.login(login(CLAVE));

        assertNotNull(usuario.getCredencial());
        assertEquals(0, usuario.getCredencial().getIntentosFallidos());
        assertFalse(usuario.getCredencial().getBloqueado());
    }

    // Login rechazado
    @Test
    void loginConEmailInexistenteFalla() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(login(CLAVE)));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void loginConCuentaInactivaFalla() {
        Usuario usuario = usuarioActivo(credencial(0, false));
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(login(CLAVE)));

        assertTrue(ex.getMessage().contains("desactivada"));
    }

    @Test
    void loginConActivoNuloFalla() {
        Usuario usuario = usuarioActivo(credencial(0, false));
        usuario.setActivo(null);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(RuntimeException.class, () -> authService.login(login(CLAVE)));
    }

    @Test
    void loginConCuentaBloqueadaFalla() {
        Usuario usuario = usuarioActivo(credencial(5, true));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(login(CLAVE)));

        assertTrue(ex.getMessage().contains("bloqueada"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // Intentos fallidos y bloqueo
    @Test
    void claveIncorrectaSumaUnIntentoFallido() {
        CredencialUsuario credencial = credencial(1, false);
        Usuario usuario = usuarioActivo(credencial);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(login("mala")));

        assertEquals(2, credencial.getIntentosFallidos());
        assertFalse(credencial.getBloqueado());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void quintoIntentoFallidoBloqueaLaCuenta() {
        CredencialUsuario credencial = credencial(4, false);
        Usuario usuario = usuarioActivo(credencial);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(login("mala")));

        assertEquals(5, credencial.getIntentosFallidos());
        assertTrue(credencial.getBloqueado());
    }

    @Test
    void primerIntentoFallidoSinCredencialLaCrea() {
        Usuario usuario = usuarioActivo(null);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(login("mala")));

        assertEquals(1, usuario.getCredencial().getIntentosFallidos());
    }

    // Registro
    @Test
    void registroCreaClienteConClaveHasheada() {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Luis");
        request.setApellido("Soto");
        request.setEmail("luis@biblioteca.cl");
        request.setPassword(CLAVE);
        UsuarioResponse respuesta = new UsuarioResponse();
        when(usuarioRepository.existsByEmail("luis@biblioteca.cl")).thenReturn(false);
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(respuesta);

        assertSame(respuesta, authService.register(request));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals("Cliente", guardado.getValue().getRol());
        assertEquals(HASH, guardado.getValue().getPassword());
        assertTrue(guardado.getValue().getActivo());
        assertFalse(guardado.getValue().getCredencial().getBloqueado());
    }

    @Test
    void registroConEmailDuplicadoFalla() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertTrue(ex.getMessage().contains(EMAIL));
        verify(usuarioRepository, never()).save(any());
    }
}
