/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;

abstract class OtlpIntegrationTest extends AbstractIntegrationTest {
  OtlpIntegrationTest(boolean configFromStdin) {
    super(configFromStdin, "otlp_config.properties");
  }

  @Container
  GenericContainer<?> cassandra =
      new GenericContainer<>("cassandra:3.11")
          .withNetwork(Network.SHARED)
          .withEnv("LOCAL_JMX", "no")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("cassandra/jmxremote.password", 0400),
              "/etc/cassandra/jmxremote.password")
          .withNetworkAliases("cassandra")
          .withExposedPorts(7199)
          .withStartupTimeout(Duration.ofSeconds(120))
          .waitingFor(Wait.forListeningPort());

  @Test
  void endToEnd() {
    waitAndAssertMetrics(
        metric -> {
          assertThat(metric.getName()).isEqualTo("cassandra.storage.load");
          assertThat(metric.getDescription())
              .isEqualTo("Size, in bytes, of the on disk data size this node manages");
          assertThat(metric.getUnit()).isEqualTo("By");
          assertThat(metric.hasHistogram()).isTrue();
          assertThat(metric.getHistogram().getDataPointsList())
              .satisfiesExactly(
                  point ->
                      assertThat(point.getAttributesList())
                          .containsExactly(
                              KeyValue.newBuilder()
                                  .setKey("myKey")
                                  .setValue(AnyValue.newBuilder().setStringValue("myVal"))
                                  .build()));
        });
  }

  static class ConfigFromStdin extends OtlpIntegrationTest {
    ConfigFromStdin() {
      super(true);
    }
  }

  static class ConfigFromFile extends OtlpIntegrationTest {
    ConfigFromFile() {
      super(false);
    }
  }
}
