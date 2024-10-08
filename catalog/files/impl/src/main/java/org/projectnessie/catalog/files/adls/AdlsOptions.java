/*
 * Copyright (C) 2024 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.catalog.files.adls;

import static org.projectnessie.catalog.secrets.SecretAttribute.secretAttribute;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.projectnessie.catalog.secrets.SecretAttribute;
import org.projectnessie.catalog.secrets.SecretType;
import org.projectnessie.catalog.secrets.SecretsProvider;
import org.projectnessie.nessie.docgen.annotations.ConfigDocs.ConfigItem;
import org.projectnessie.nessie.docgen.annotations.ConfigDocs.ConfigPropertyName;

public interface AdlsOptions {

  /**
   * For custom ADLS configuration options, consult javadocs for {@code
   * com.azure.core.util.Configuration}.
   */
  Map<String, String> configurationOptions();

  /** Override the default read block size used when writing to ADLS. */
  OptionalInt readBlockSize();

  /** Override the default write block size used when writing to ADLS. */
  OptionalLong writeBlockSize();

  /**
   * Default file-system configuration, default/fallback values for all file-systems are taken from
   * this one.
   */
  @ConfigItem(section = "default-options")
  Optional<? extends AdlsFileSystemOptions> defaultOptions();

  /** ADLS file-system specific options, per file system name. */
  @ConfigItem(section = "buckets")
  @ConfigPropertyName("filesystem-name")
  Map<String, ? extends AdlsNamedFileSystemOptions> fileSystems();

  List<SecretAttribute<AdlsFileSystemOptions, ImmutableAdlsNamedFileSystemOptions.Builder, ?>>
      SECRET_ATTRIBUTES =
          ImmutableList.of(
              secretAttribute(
                  "account",
                  SecretType.BASIC,
                  AdlsFileSystemOptions::account,
                  ImmutableAdlsNamedFileSystemOptions.Builder::account),
              secretAttribute(
                  "sasToken",
                  SecretType.KEY,
                  AdlsFileSystemOptions::sasToken,
                  ImmutableAdlsNamedFileSystemOptions.Builder::sasToken));

  default AdlsFileSystemOptions resolveSecrets(
      String filesystemName, AdlsFileSystemOptions specific, SecretsProvider secretsProvider) {
    AdlsFileSystemOptions defaultOptions =
        defaultOptions()
            .map(AdlsFileSystemOptions.class::cast)
            .orElse(AdlsNamedFileSystemOptions.FALLBACK);

    ImmutableAdlsNamedFileSystemOptions.Builder builder =
        ImmutableAdlsNamedFileSystemOptions.builder().from(defaultOptions);
    if (specific != null) {
      builder.from(specific);
    }

    return secretsProvider
        .applySecrets(
            builder,
            "object-stores.adls",
            defaultOptions,
            filesystemName,
            specific,
            SECRET_ATTRIBUTES)
        .build();
  }

  default void validate() {
    boolean hasDefaultEndpoint = defaultOptions().map(o -> o.endpoint().isPresent()).orElse(false);
    if (!hasDefaultEndpoint && !fileSystems().isEmpty()) {
      List<String> missing =
          fileSystems().entrySet().stream()
              .filter(e -> e.getValue().endpoint().isEmpty())
              .map(Map.Entry::getKey)
              .sorted()
              .collect(Collectors.toList());
      if (!missing.isEmpty()) {
        String msg =
            missing.stream()
                .collect(
                    Collectors.joining(
                        "', '",
                        "Mandatory ADLS endpoint is not configured for file system '",
                        "'."));
        throw new IllegalStateException(msg);
      }
    }
  }

  default AdlsFileSystemOptions effectiveOptionsForFileSystem(
      Optional<String> filesystemName, SecretsProvider secretsProvider) {
    if (filesystemName.isEmpty()) {
      return resolveSecrets(null, null, secretsProvider);
    }
    String name = filesystemName.get();
    AdlsFileSystemOptions fileSystem = fileSystems().get(name);
    return resolveSecrets(name, fileSystem, secretsProvider);
  }
}
