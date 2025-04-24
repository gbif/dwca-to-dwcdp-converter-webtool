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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileService {

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

  public List<String> listFilesInZip(MultipartFile file) {
    List<String> fileNames = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          fileNames.add(entry.getName());
        }
      }
    } catch (IOException e) {
      fileNames.add("Error reading ZIP contents.");
    }
    return fileNames;
  }

  public List<String> listFilesInZip(File file) {
    List<String> fileNames = new ArrayList<>();

    if (file != null) {
      try (FileInputStream fis = new FileInputStream(file);
           ZipInputStream zis = new ZipInputStream(fis)) {

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (!entry.isDirectory()) {
            fileNames.add(entry.getName());
          }
        }

      } catch (IOException e) {
        fileNames.add("Error reading ZIP contents.");
      }
    }

    return fileNames;
  }

  public File findZipInDirectory(String dirPath) {
    File dir = new File(dirPath);
    if (!dir.exists() || !dir.isDirectory()) return null;

    File[] zipFiles = dir.listFiles((d, name) -> name.endsWith(".zip"));
    if (zipFiles == null || zipFiles.length == 0) return null;

    return Arrays.stream(zipFiles)
        .max(Comparator.comparingLong(File::lastModified))
        .orElse(null);
  }

  public File convertToFile(MultipartFile multipartFile) throws IOException {
    File convFile = File.createTempFile("dwca_to_dwcdp_", "_" + multipartFile.getOriginalFilename());
    try (FileOutputStream fos = new FileOutputStream(convFile)) {
      fos.write(multipartFile.getBytes());
    }
    return convFile;
  }
}
