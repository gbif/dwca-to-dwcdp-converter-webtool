/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.dwcatodwcdp.converter.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileValidator {

  public boolean isValidDarwinCoreArchive(MultipartFile file) {
    if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
      return false;
    }

    try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
      ZipEntry entry;
      boolean hasMetaXml = false;

      while ((entry = zis.getNextEntry()) != null) {
        if ("meta.xml".equalsIgnoreCase(entry.getName())) {
          hasMetaXml = true;
        }
        zis.closeEntry();
      }

      return hasMetaXml;
    } catch (IOException e) {
      return false;
    }
  }

}
