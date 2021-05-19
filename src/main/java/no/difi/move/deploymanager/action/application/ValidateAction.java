package no.difi.move.deploymanager.action.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.difi.move.deploymanager.action.DeployActionException;
import no.difi.move.deploymanager.config.DeployManagerProperties;
import no.difi.move.deploymanager.domain.application.Application;
import no.difi.move.deploymanager.repo.DeployDirectoryRepo;
import no.difi.move.deploymanager.repo.NexusRepo;
import no.difi.move.deploymanager.service.codesigner.GpgService;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class ValidateAction implements ApplicationAction {

    private final NexusRepo nexusRepo;
    private final GpgService gpgService;
    private final DeployDirectoryRepo deployDirectoryRepo;
    private final DeployManagerProperties properties;

    @Override
    public Application apply(Application application) {
        if (!application.isMarkedForValidation()){
            log.info("Skipping validation, as no new distribution has been downloaded");
            return application;
        }
        log.info("Validating application");
        log.trace("Calling ValidateAction.apply on application {}", application);
        try {
            assertChecksumIsCorrect(application, ALGORITHM.SHA1);
            assertChecksumIsCorrect(application, ALGORITHM.MD5);
            String signature = downloadSignature(application.getLatest().getVersion());
            boolean verify = gpgService.verify(application.getLatest().getFile().getAbsolutePath(), signature);
            if (verify) {
                log.trace("Signature has been successfully verified.");
                return application;
            }
            if(properties.getBlocklist().isEnabled()) {
                log.trace("Signature could not be verified.. Blocklisting version.");
                deployDirectoryRepo.blockList(application.getLatest().getFile());
            }
            throw new DeployActionException("Invalid artifact signature");
        } catch (Exception ex) {
            throw new DeployActionException("Error validating jar", ex);
        }
    }

    private void assertChecksumIsCorrect(Application application, ALGORITHM algorithm) throws IOException, NoSuchAlgorithmException {
        log.trace("Calling ValidateAction.assertChecksumIsCorrect() with args: application: {}, algorithm: {}", application, algorithm);
        byte[] hashFromRepo = getHashFromRepo(application.getLatest().getVersion(), algorithm);
        log.trace("Hash from repo: {}", hashFromRepo);
        byte[] fileHash = getFileHash(application.getLatest().getFile(), algorithm);
        log.trace("File hash: {}", fileHash);
        if (!MessageDigest.isEqual(fileHash, hashFromRepo)) {
            throw new DeployActionException(String.format("%s verification failed", algorithm.getName()));
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private byte[] getFileHash(File file, ALGORITHM algorithm) throws IOException, NoSuchAlgorithmException {
        byte[] buffer = new byte[8192];
        MessageDigest instance = MessageDigest.getInstance(algorithm.getName());
        try (DigestInputStream digestInputStream = new DigestInputStream(new FileInputStream(file), instance)) {
            while (digestInputStream.read(buffer) != -1) ;
            return instance.digest();
        }
    }

    private byte[] getHashFromRepo(String applicationVersion, ALGORITHM algorithm) {
        return nexusRepo.getChecksum(applicationVersion, "jar." + algorithm.getFileNameSuffix());
    }

    @RequiredArgsConstructor
    @Getter
    private enum ALGORITHM {
        MD5("MD5", "md5"),
        SHA1("SHA-1", "sha1");

        private final String name;
        private final String fileNameSuffix;
    }

    private String downloadSignature(String version) {
        String signature = nexusRepo.downloadSignature(version);
        log.trace("Downloaded signature {} ", signature);
        return signature;
    }

}
