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

import static java.util.function.Function.identity;
import static org.projectnessie.catalog.secrets.BasicCredentials.basicCredentials;

import com.azure.core.http.HttpClient;
import java.util.Map;
import java.util.stream.Collectors;
import org.projectnessie.catalog.files.AbstractClients;
import org.projectnessie.catalog.files.api.BackendExceptionMapper;
import org.projectnessie.catalog.files.api.ObjectIO;
import org.projectnessie.catalog.secrets.SecretsProvider;
import org.projectnessie.objectstoragemock.ObjectStorageMock;
import org.projectnessie.storage.uri.StorageUri;

public class TestAdlsClients extends AbstractClients {

  @Override
  protected StorageUri buildURI(String bucket, String key) {
    return StorageUri.of(String.format("abfs://%s@storageAccount/%s", bucket, key));
  }

  @Override
  protected BackendExceptionMapper.Builder addExceptionHandlers(
      BackendExceptionMapper.Builder builder) {
    return builder.addAnalyzer(AdlsExceptionMapper.INSTANCE);
  }

  @Override
  protected ObjectIO buildObjectIO(
      ObjectStorageMock.MockServer server1, ObjectStorageMock.MockServer server2) {
    HttpClient httpClient = AdlsClients.buildSharedHttpClient(AdlsConfig.builder().build());

    ImmutableAdlsProgrammaticOptions.Builder adlsOptions =
        ImmutableAdlsProgrammaticOptions.builder()
            .putFileSystems(
                BUCKET_1,
                ImmutableAdlsNamedFileSystemOptions.builder()
                    .endpoint(server1.getAdlsGen2BaseUri().toString())
                    .authType(AdlsFileSystemOptions.AzureAuthType.STORAGE_SHARED_KEY)
                    .account(basicCredentials("accountName", "accountKey"))
                    .build());
    if (server2 != null) {
      adlsOptions.putFileSystems(
          BUCKET_2,
          ImmutableAdlsNamedFileSystemOptions.builder()
              .endpoint(server2.getAdlsGen2BaseUri().toString())
              .authType(AdlsFileSystemOptions.AzureAuthType.STORAGE_SHARED_KEY)
              .account(basicCredentials("accountName", "accountKey"))
              .build());
    }

    SecretsProvider secretsProvider =
        new SecretsProvider(
            names ->
                names.stream()
                    .collect(Collectors.toMap(identity(), k -> Map.of("secret", "secret"))));

    AdlsClientSupplier supplier =
        new AdlsClientSupplier(httpClient, adlsOptions.build(), secretsProvider);

    return new AdlsObjectIO(supplier);
  }
}
