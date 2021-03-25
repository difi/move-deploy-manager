package no.difi.move.deploymanager.service.codesigner;

import lombok.extern.slf4j.Slf4j;
import no.difi.move.deploymanager.action.DeployActionException;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;

@Service
@Slf4j
public class GpgServiceImpl implements GpgService {

    private static final JcaKeyFingerprintCalculator KEY_FINGERPRINT_CALCULATOR = new JcaKeyFingerprintCalculator();

    @Override
    public boolean verify(String signedData, String downloadedSignature, List<String> publicKeyFiles) {
        if (isNullOrEmpty(signedData) || isNullOrEmpty(downloadedSignature)) {
            throw new IllegalArgumentException("One or multiple values are null. " +
                    "\nSignedDataFilePath: " + signedData +
                    "\nSignature: " + downloadedSignature);
        }
        if (publicKeyFiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot verify signature due to missing keys");
        }
        log.info("Verifying signed data");
        PGPSignature signature = Optional.ofNullable(readSignature(downloadedSignature))
                .orElseThrow(() -> new DeployActionException(
                        String.format("Unable to read PGP signature from %s", downloadedSignature)));
        PGPPublicKey signerKey = publicKeyFiles.stream()
                .map(this::readPublicKey)
                .filter(Objects::nonNull)
                .map(file -> getSignerKey(signature, file))
                .filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new DeployActionException("Signer public key not found in keyring"));
        return doVerify(signedData, signature, signerKey);
    }

    private boolean doVerify(String signedData, PGPSignature signature, PGPPublicKey publicKey) {
        log.debug("Attempting GPG verification with public key {}", publicKey.getKeyID());
        try (InputStream signedDataStream = new FileInputStream(signedData)) {
            signature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = signedDataStream.read(buffer)) != -1) {
                signature.update(buffer, 0, read);
            }
            return signature.verify();
        } catch (IOException e) {
            log.error("Could not read the signed data", e);
        } catch (PGPException e) {
            log.error("Could not verify GPG signature", e);
        }
        log.debug("Verification failed for key {}", publicKey.getKeyID());
        return false;
    }

    private PGPPublicKey getSignerKey(PGPSignature signature, PGPPublicKeyRingCollection file) {
        log.info("Looking for signer key");
        final long keyID = signature.getKeyID();
        log.trace("Looking for signer key {} in file {}", keyID, file);
        try {
            return file.getPublicKey(keyID);
        } catch (PGPException e) {
            log.warn("Could not get signer public key from file {}", file);
        }
        return null;
    }

    private PGPPublicKeyRingCollection readPublicKey(String key) {
        log.info("Reading public key file");
        try (InputStream publicKey = new ByteArrayInputStream(key.getBytes())) {
            return new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicKey), KEY_FINGERPRINT_CALCULATOR);
        } catch (IOException e) {
            log.warn("Could not read public key from {}", key);
        } catch (PGPException e) {
            log.warn("Invalid public key encountered in {}", key);
        }
        return null;
    }

    private PGPSignature readSignature(String signature) {
        log.info("Reading PGP signature");
        try (InputStream signatureStream = new ByteArrayInputStream(signature.getBytes())) {
            PGPObjectFactory pgpFactory = new PGPObjectFactory(PGPUtil.getDecoderStream(signatureStream), KEY_FINGERPRINT_CALCULATOR);
            PGPSignatureList pgpSignatures = Optional.ofNullable((PGPSignatureList) pgpFactory.nextObject())
                    .orElseThrow(() -> new DeployActionException("Unable to read signature"));
            return pgpSignatures.get(0);
        } catch (IOException e) {
            log.warn("Could not read signature from {}", signature);
        }
        return null;
    }
}
