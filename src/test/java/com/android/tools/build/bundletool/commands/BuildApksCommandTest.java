/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.testing.Aapt2Helper.AAPT2_PATH;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeAndroidHomeVariableProvider;
import com.android.tools.build.bundletool.testing.FakeAndroidSerialVariableProvider;
import com.android.tools.build.bundletool.utils.EnvironmentVariableProvider;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import com.android.tools.build.bundletool.utils.flags.FlagParser.FlagParseException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class BuildApksCommandTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";
  private static final Path ADB_PATH =
      Paths.get("third_party/java/android/android_sdk_linux/platform-tools/adb.static");
  private static final String DEVICE_ID = "id1";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private final Path bundlePath = Paths.get("/path/to/bundle.aab");
  private final Path outputFilePath = Paths.get("/path/to/app.apks");
  private Path tmpDir;
  private Path keystorePath;

  private final AdbServer fakeAdbServer = mock(AdbServer.class);
  private final EnvironmentVariableProvider androidHomeProvider =
      new FakeAndroidHomeVariableProvider("/android/home");
  private final EnvironmentVariableProvider androidSerialProvider =
      new FakeAndroidSerialVariableProvider(DEVICE_ID);

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    tmpDir = tmp.getRoot().toPath();

    // KeyStore.
    keystorePath = tmpDir.resolve("keystore.jks");
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, KEYSTORE_PASSWORD.toCharArray());
    keystore.setKeyEntry(
        KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), new Certificate[] {certificate});
    keystore.store(new FileOutputStream(keystorePath.toFile()), KEYSTORE_PASSWORD.toCharArray());

    fakeAdbServer.init(Paths.get("path/to/adb"));
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOptimizeFor() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--optimize-for=screen_density"),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOptimizationDimensions(ImmutableSet.of(SCREEN_DENSITY))
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalSigning() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setSigningConfiguration(
                SigningConfiguration.builder()
                    .setPrivateKey(privateKey)
                    .setCertificates(ImmutableList.of(certificate))
                    .build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalUniversal() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--universal"),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setGenerateOnlyUniversalApk(true)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOverwrite() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--overwrite"),
            fakeAdbServer);
    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOverwriteOutput(true)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_deviceId() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--device-id=" + DEVICE_ID,
                    "--connected-device",
                    "--adb=" + ADB_PATH,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceId(DEVICE_ID)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_androidSerialVariable() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--connected-device",
                    "--adb=" + ADB_PATH,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            androidSerialProvider,
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceId(DEVICE_ID)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void outputNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "outputFile",
        () ->
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setAapt2Command(aapt2Command)
                .build());

    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildApksCommand.fromFlags(
                new FlagParser().parse("--bundle=" + bundlePath), fakeAdbServer));
  }

  @Test
  public void bundleNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "bundlePath",
        () ->
            BuildApksCommand.builder()
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .build());

    expectMissingRequiredFlagException(
        "bundle",
        () ->
            BuildApksCommand.fromFlags(
                new FlagParser().parse("--output=" + outputFilePath), fakeAdbServer));
  }

  @Test
  public void optimizationDimensionsWithUniversal_throws() throws Exception {
    CommandExecutionException builderException =
        assertThrows(
            CommandExecutionException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setAapt2Command(aapt2Command)
                    .setGenerateOnlyUniversalApk(true)
                    .setOptimizationDimensions(ImmutableSet.of(ABI))
                    .build());
    assertThat(builderException)
        .hasMessageThat()
        .contains("Cannot generate universal APK and specify optimization dimensions");

    ValidationException flagsException =
        assertThrows(
            ValidationException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--optimize-for=abi",
                            "--universal"),
                    fakeAdbServer));
    assertThat(flagsException)
        .hasMessageThat()
        .contains("Cannot generate universal APK and specify optimization dimensions");
  }

  @Test
  public void nonPositiveMaxThreads_throws() throws Exception {
    FlagParseException zeroException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--max-threads=0"),
                    fakeAdbServer));
    assertThat(zeroException).hasMessageThat().contains("flag --max-threads has illegal value");

    FlagParseException negativeException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--max-threads=-1"),
                    fakeAdbServer));
    assertThat(negativeException).hasMessageThat().contains("flag --max-threads has illegal value");
  }

  @Test
  public void positiveMaxThreads_succeeds() throws Exception {
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--max-threads=3"),
        fakeAdbServer);
  }

  @Test
  public void keyStoreFlags_keyAliasNotSet() {
    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks=" + keystorePath),
                    fakeAdbServer));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks-key-alias is required when --ks is set.");
  }

  @Test
  public void keyStoreFlags_keyStoreNotSet() {
    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks-key-alias=" + KEY_ALIAS),
                    fakeAdbServer));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks is required when --ks-key-alias is set.");
  }

  @Test
  public void printHelpDoesNotCrash() {
    BuildApksCommand.help();
  }

  @Test
  public void noKeystoreProvidedPrintsWarning() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--aapt2=" + AAPT2_PATH),
        new PrintStream(output),
        androidHomeProvider,
        fakeAdbServer);

    assertThat(new String(output.toByteArray(), UTF_8))
        .contains("WARNING: The APKs won't be signed");
  }

  @Test
  public void keystoreProvidedDoesNotPrintWarning() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse(
                "--bundle=" + bundlePath,
                "--output=" + outputFilePath,
                "--aapt2=" + AAPT2_PATH,
                "--ks=" + keystorePath,
                "--ks-key-alias=" + KEY_ALIAS,
                "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                "--key-pass=pass:" + KEY_PASSWORD),
        new PrintStream(output),
        androidHomeProvider,
        fakeAdbServer);

    assertThat(new String(output.toByteArray(), UTF_8))
        .doesNotContain("WARNING: The APKs won't be signed");
  }
}
