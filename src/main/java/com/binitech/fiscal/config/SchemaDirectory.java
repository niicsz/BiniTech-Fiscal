package com.binitech.fiscal.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class SchemaDirectory {

  private SchemaDirectory() {}

  public static String extract() {
    try {
      Path directory = Files.createTempDirectory("nfe-schemas");
      Resource[] schemas =
          new PathMatchingResourcePatternResolver().getResources("classpath:nfe-schemas/*.xsd");
      if (schemas.length == 0) {
        throw new IllegalStateException("Schemas XSD da NF-e não encontrados no classpath.");
      }
      for (Resource schema : schemas) {
        try (InputStream in = schema.getInputStream()) {
          Files.copy(
              in, directory.resolve(schema.getFilename()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      return directory.toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao preparar os schemas da NF-e.", e);
    }
  }
}
