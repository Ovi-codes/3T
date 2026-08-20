package ro.threet.run.ping;

/**
 * Body of {@code GET /api/ping}, e.g. {@code {"status":"ok","appVersion":"0.0.1"}}.
 */
public record PingResponse(String status, String appVersion) {
}