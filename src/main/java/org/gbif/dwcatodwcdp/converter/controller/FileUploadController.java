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

import org.gbif.datapackage.DataPackageFieldMapping;
import org.gbif.dwcatodwcdp.converter.DwcaToDwcDpConverter;
import org.gbif.dwcatodwcdp.converter.service.FileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class FileUploadController {

  private static final Logger LOG = LoggerFactory.getLogger(FileUploadController.class);

  @Autowired
  private FileService fileService;
  @Autowired
  private DwcaToDwcDpConverter dwcaToDwcDpConverter;
  @Value("${converter.output.directory}")
  private String outputDirectoryStr;
  @Value("${converter.mappings}")
  private String mappingsDirectoryStr;
  @Autowired
  private ObjectMapper jacksonObjectMapper;

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
      File inputDwca = fileService.convertToFile(file);
      File outputDirectory = new File(outputDirectoryStr);
      File mappingsDirectory = new File(mappingsDirectoryStr);

      dwcaToDwcDpConverter.convert(inputDwca, outputDirectory, mappingsDirectory);

      File dwcDpArchive = fileService.findZipInDirectory(outputDirectoryStr);

      if (dwcDpArchive != null) {
        LOG.info("DwC DP archive file: {}", dwcDpArchive.getName());
      }
      List<String> dwcDpFileList = fileService.listFilesInZip(dwcDpArchive);

      model.addAttribute("dwcDpFiles", dwcDpFileList);

      File dwcdpArchive = fileService.findZipInDirectory(outputDirectoryStr);
      String dwcdpFileName = dwcdpArchive.getName()
          .replace(".zip", "");
      Map<String, List<DataPackageFieldMapping>> mappings = readMappingFiles(new File(outputDirectory, "mappings-" + dwcdpFileName));
      model.addAttribute("mappings", mappings);
    } catch (IOException e) {
      // TODO: process
      throw new RuntimeException(e);
    }

    return "result";
  }

  @GetMapping("/download")
  public ResponseEntity<Resource> downloadArchive() throws IOException {
    File zipFile = fileService.findZipInDirectory(outputDirectoryStr);

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

  private Map<String, List<DataPackageFieldMapping>> readMappingFiles(File directory) throws IOException {
    Map<String, List<DataPackageFieldMapping>> result = new HashMap<>();

    if (!directory.exists() || !directory.isDirectory()) {
      throw new IllegalArgumentException("Invalid directory: " + directory.getAbsolutePath());
    }

    File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null) return result;

    for (File file : files) {
      // Use the filename (without .json) as the key
      String key = file.getName().replaceFirst("[.][^.]+$", "");
      List<DataPackageFieldMapping> value = jacksonObjectMapper.readValue(file, new TypeReference<>() {
      });
      result.put(key, value);
    }

    return result;
  }
}
