package untrm.hotel_san_antonio.servicio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.RecuperacionDAO;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.PasswordUtil;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/** Recuperación asistida por administrador cuando no existe SMTP configurado. */
public class RecuperacionService {
    private final UsuarioDAO usuarios = new UsuarioDAO();
    private final RecuperacionDAO tokens = new RecuperacionDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    public String emitirToken(String nombreUsuario) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        byte[] azar = new byte[32];
        new SecureRandom().nextBytes(azar);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(azar);
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Usuario destinatario = usuarios.buscarPorUsuario(con, nombreUsuario.trim());
                if (destinatario == null) throw new IllegalArgumentException("Usuario activo no encontrado.");
                tokens.anularPendientes(con, destinatario.getIdUsuario());
                tokens.insertar(con, destinatario.getIdUsuario(), digest(token));
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "RECUPERACION_EMITIR", "usuario", destinatario.getIdUsuario(), null);
                con.commit();
                return token;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    public void restablecer(String nombreUsuario, String token, String nueva) throws SQLException {
        if (nombreUsuario == null || nombreUsuario.isBlank() || token == null || token.isBlank())
            throw new IllegalArgumentException("Usuario o token inválido.");
        PasswordUtil.validarNueva(nueva);
        String hashToken = digest(token.trim());
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Usuario usuario = usuarios.buscarPorUsuario(con, nombreUsuario.trim());
                if (usuario == null) throw new IllegalArgumentException("Usuario o token inválido.");
                RecuperacionDAO.TokenPendiente valido = null;
                for (var candidato : tokens.vigentes(con, usuario.getIdUsuario())) {
                    if (MessageDigest.isEqual(hashToken.getBytes(StandardCharsets.US_ASCII),
                            candidato.hash().getBytes(StandardCharsets.US_ASCII))) valido = candidato;
                }
                if (valido == null) throw new IllegalArgumentException("Usuario o token inválido.");
                usuarios.actualizarContrasena(con, usuario.getIdUsuario(), PasswordUtil.hash(nueva));
                tokens.usar(con, valido.id());
                auditoria.registrar(con, usuario.getIdUsuario(), "CONTRASENA_RESTABLECIDA",
                        "usuario", usuario.getIdUsuario(), "Mediante token temporal de administrador");
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    private String digest(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo procesar el token.", error);
        }
    }
}
