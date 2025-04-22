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
package org.gbif.dwcatodwcdp.converter.controller;

import org.gbif.dwcatodwcdp.converter.DwcaToDwcDpConverter;
import org.gbif.dwcatodwcdp.converter.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Controller
public class FileUploadController {

  @Autowired
  private FileService fileService;
  @Autowired
  private DwcaToDwcDpConverter dwcaToDwcDpConverter;
  @Value("${converter.output.directory}")
  private String outputDirectoryStr;
  @Value("${converter.mappings}")
  private String mappingsDirectoryStr;

  @GetMapping("/")
  public String showUploadForm() {
    return "upload";
  }

  @PostMapping("/upload")
  public String handleFileUpload(@RequestParam("file") MultipartFile file, Model model) {
    if (!fileService.isValidDarwinCoreArchive(file)) {
      model.addAttribute("dwcaResult", "Invalid Darwin Core Archive: 'meta.xml' not found.");
      return "result";
    }

    String result = "Valid Darwin Core Archive uploaded.";
    List<String> fileList = fileService.listFilesInZip(file);

    model.addAttribute("dwcaResult", result);
    model.addAttribute("dwcaFiles", fileList);

    try {
      File inputDwca = convertToFile(file);
      File outputDirectory = new File(outputDirectoryStr);
      File mappingsDirectory = new File(mappingsDirectoryStr);

      dwcaToDwcDpConverter.convert(inputDwca, outputDirectory, mappingsDirectory);

      File dwcDpArchive = findZipInDirectory(outputDirectoryStr);
      List<String> dwcDpFileList = fileService.listFilesInZip(dwcDpArchive);

      model.addAttribute("dwcDpFiles", dwcDpFileList);
    } catch (IOException e) {
      // TODO: process
      throw new RuntimeException(e);
    }

    return "result";
  }

  @GetMapping("/download")
  public ResponseEntity<Resource> downloadArchive() throws IOException {
    File zipFile = findZipInDirectory(outputDirectoryStr);

    if (zipFile == null || !zipFile.exists()) {
      return ResponseEntity.notFound().build();
    }

    InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + zipFile.getName() + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(zipFile.length())
        .body(resource);
  }

  private File findZipInDirectory(String dirPath) {
    File dir = new File(dirPath);
    if (!dir.exists() || !dir.isDirectory()) return null;

    File[] zipFiles = dir.listFiles((d, name) -> name.endsWith(".zip"));
    if (zipFiles == null || zipFiles.length == 0) return null;

    return Arrays.stream(zipFiles)
        .max(Comparator.comparingLong(File::lastModified))
        .orElse(null);
  }

  private File convertToFile(MultipartFile multipartFile) throws IOException {
    File convFile = File.createTempFile("dwca_to_dwcdp_", "_" + multipartFile.getOriginalFilename());
    try (FileOutputStream fos = new FileOutputStream(convFile)) {
      fos.write(multipartFile.getBytes());
    }
    return convFile;
  }
}
