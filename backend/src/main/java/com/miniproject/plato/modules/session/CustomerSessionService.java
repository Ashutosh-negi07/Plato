package com.miniproject.plato.modules.session;

import com.miniproject.plato.modules.session.dto.CustomerSessionResponse;
import com.miniproject.plato.modules.session.dto.StartSessionRequest;

import java.util.UUID;

public interface CustomerSessionService {

    // Customer scans QR code -> starts or joins active session
    CustomerSessionResponse startSession(StartSessionRequest request);

    // Customer app checks current session & refreshes sliding 30-min window
    CustomerSessionResponse getCurrentSession(String sessionToken);

    // Validates token, refreshes sliding expiration, and returns the entity
    CustomerSession validateAndRefreshSession(String sessionToken);

    // Staff closes session manually (e.g. after cash bill settlement)
    void closeSession(UUID sessionId, UUID callerId, String callerRole);
}
