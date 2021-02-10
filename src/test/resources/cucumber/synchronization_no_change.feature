Feature: Synchronization - No Change

  Background:
    Given the metadata.properties contains:
    """
    version=1.7.93-SNAPSHOT
    filename=integrasjonspunkt-1.7.93-SNAPSHOT.jar
    repositoryId=staging
    sha1=39ba01879f7ededa62f7e5129f140089795e05bc
    """
    And the "integrasjonspunkt-1.7.93-SNAPSHOT.jar" exists as a copy of "/cucumber/success.jar"
    And the latest integrasjonspunkt version is "1.7.93-SNAPSHOT"
    And the supported major version is unset
    And a "GET" request to "https://beta-meldingsutveksling.difi.no/service/local/artifact/maven/content?r=staging&g=no.difi.meldingsutveksling&a=integrasjonspunkt&v=1.7.93-SNAPSHOT" will respond with status "200" and the following "application/java-archive" in "/cucumber/success.jar"
    And a "GET" request to "https://beta-meldingsutveksling.difi.no/service/local/artifact/maven/content?r=staging&g=no.difi.meldingsutveksling&a=integrasjonspunkt&v=1.7.93-SNAPSHOT&e=jar.sha1" will respond with status "200" and the following "application/octet-stream"
    """
    39ba01879f7ededa62f7e5129f140089795e05bc
    """
    And a "GET" request to "https://beta-meldingsutveksling.difi.no/service/local/artifact/maven/content?r=staging&g=no.difi.meldingsutveksling&a=integrasjonspunkt&v=1.7.93-SNAPSHOT&e=jar.md5" will respond with status "200" and the following "application/octet-stream"
    """
    e343ab4e4151f822331e7f5998b26ecc
    """
    And a "GET" request to "http://localhost:9092/manage/health" will respond with status "200" and the following "application/vnd.spring-boot.actuator.v1+json;charset=UTF-8"
    """
    {
        "status": "UP"
    }
    """
    And a "GET" request to "http://localhost:9092/manage/health" will respond with status "200" and the following "application/vnd.spring-boot.actuator.v1+json;charset=UTF-8"
    """
    {
        "status": "UP"
    }
    """
  Scenario: No change
    Given the synchronization handler is triggered
    Then no JAR is launched
    And the metadata.properties is updated with:
    """
    version=1.7.93-SNAPSHOT
    filename=integrasjonspunkt-1.7.93-SNAPSHOT.jar
    repositoryId=staging
    sha1=39ba01879f7ededa62f7e5129f140089795e05bc
    """
    And no emails are sent
