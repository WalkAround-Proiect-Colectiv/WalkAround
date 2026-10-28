package services.applicationapi.register.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.register.dto.RegistrationResponse;
import services.applicationapi.user.model.*;
import services.applicationapi.user.repository.*;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);
    private final GoogleIdentityVerifier verifier;
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final TransactionTemplate transactions;

    public RegistrationServiceImpl(GoogleIdentityVerifier verifier, UserRepository users,
                                   UserIdentityRepository identities, TransactionTemplate transactions) {
        this.verifier = verifier;
        this.users = users;
        this.identities = identities;
        this.transactions = transactions;
    }

    @Override
    public RegistrationResponse registerWithGoogle(String idToken) {
        // Verify with Google before opening a database transaction.
        GoogleIdentity identity = verifier.verify(idToken);
        try {
            RegistrationResponse response = transactions.execute(status -> {
                if (identities.existsByProviderAndProviderSubject("google", identity.subject())) {
                    throw alreadyRegistered();
                }
                User user = users.save(new User(identity.firstName(), identity.lastName(), identity.pictureUrl()));
                identities.saveAndFlush(new UserIdentity(user, "google", identity.subject(), identity.email()));
                return RegistrationResponse.from(user);
            });
            log.info("User registration completed: userId={}", response.id());
            return response;
        } catch (DataIntegrityViolationException ex) {
            // The losing concurrent request is rolled back, including its new user.
            if (identities.existsByProviderAndProviderSubject("google", identity.subject())) {
                throw alreadyRegistered();
            }
            throw ex;
        }
    }

    private ResponseStatusException alreadyRegistered() {
        log.info("Google registration rejected: identity already registered");
        return new ResponseStatusException(HttpStatus.CONFLICT, "Account already registered; please sign in");
    }
}
