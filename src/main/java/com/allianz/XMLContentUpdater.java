package com.allianz;

import com.opencsv.exceptions.CsvException;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.opencsv.CSVReader;

public class XMLContentUpdater {

  public static void main(String[] args) {
    String inputCsvPath = "C:\\Users\\Nursultan Sadyk\\Desktop\\updated.csv"; // Update this path
    String sourceZipPath = "C:\\Users\\Nursultan Sadyk\\Desktop\\cc-master-lang.zip"; // Update this path
    String extractedFilesDir = "C:\\Users\\Nursultan Sadyk\\Desktop\\extractedFiles"; // Update this path
    String outputZipPath = "C:\\Users\\Nursultan Sadyk\\Desktop\\updated-cc-master-lang.zip"; // Update this path

    try {
      unzip(sourceZipPath, extractedFilesDir);
      List<String[]> csvData = readCsv(inputCsvPath);
      updateXmlFiles(Paths.get(extractedFilesDir), csvData);
      zipDirectory(Paths.get(extractedFilesDir), Paths.get(outputZipPath));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void unzip(String zipFilePath, String destDirectory) throws IOException {
    File destDir = new File(destDirectory);
    if (!destDir.exists()) {
      destDir.mkdirs();
    }
    byte[] buffer = new byte[1024];
    ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
    ZipEntry zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      File newFile = new File(destDirectory, zipEntry.getName());
      if (zipEntry.isDirectory()) {
        newFile.mkdirs();
      } else {
        new File(newFile.getParent()).mkdirs();
        FileOutputStream fos = new FileOutputStream(newFile);
        int len;
        while ((len = zis.read(buffer)) > 0) {
          fos.write(buffer, 0, len);
        }
        fos.close();
      }
      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
    zis.close();
  }

  public static List<String[]> readCsv(String filePath) throws IOException, CsvException {
    CSVReader reader = new CSVReader(new FileReader(filePath));
    List<String[]> data = reader.readAll();
    reader.close();
    return data;
  }

  public static void updateXmlFiles(Path dirPath, List<String[]> csvData) throws Exception {
    Map<String, Map<String, String>> translations = new HashMap<>();

    // Parse CSV data
    String[] headers = csvData.get(0);
    for (int i = 1; i < csvData.size(); i++) {
      String[] line = csvData.get(i);
      String file = line[0].replace("\\", "/").toLowerCase();
      String field = line[1];
      for (int j = 2; j < line.length; j++) {
        String language = headers[j].toLowerCase();
        String content = line[j];
        if (content != null && !content.isEmpty()) {
          translations
              .computeIfAbsent(file + "|" + field, k -> new HashMap<>())
              .put(language, content);
        }
      }
    }

    // Update XML files
    Files.walk(dirPath)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".content.xml"))
        .forEach(path -> {
          try {
            File xmlFile = path.toFile();
            String relativePath = dirPath.relativize(path).toString().replace("\\", "/").toLowerCase();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList dataNodes = doc.getElementsByTagName("data");
            for (int i = 0; i < dataNodes.getLength(); i++) {
              Node dataNode = dataNodes.item(i);
              if (dataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element dataElement = (Element) dataNode;
                Map<String, Element> existingLanguageElements = new HashMap<>();
                NodeList languageNodes = dataElement.getChildNodes();

                // Collect existing language elements
                for (int j = 0; j < languageNodes.getLength(); j++) {
                  Node langNode = languageNodes.item(j);
                  if (langNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element langElement = (Element) langNode;
                    existingLanguageElements.put(langElement.getNodeName().toLowerCase(), langElement);
                  }
                }

                // Create new language elements based on master if they do not already exist
                for (Map.Entry<String, Map<String, String>> entry : translations.entrySet()) {
                  String fileField = entry.getKey();
                  String[] parts = fileField.split("\\|");
                  String file = parts[0];
                  String field = parts[1];

                  if (relativePath.replace("/", "").equals(file)) {
                    for (Map.Entry<String, String> langEntry : entry.getValue().entrySet()) {
                      String language = langEntry.getKey();
                      String content = langEntry.getValue();

                      if (!existingLanguageElements.containsKey(language)) {
                        Element newLangElement = doc.createElement(language);
                        copyAttributesAndChildren(existingLanguageElements.get("master"), newLangElement, field, content);
                        dataElement.appendChild(newLangElement);
                      } else {
                        // Update existing language element with content from CSV if it matches the language
                        if (existingLanguageElements.containsKey(language)) {
                          existingLanguageElements.get(language).setAttribute(field, content);
                        }
                      }
                    }
                  }
                }
              }
            }

            // Save updated XML file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(xmlFile);
            transformer.transform(source, result);
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }

  public static void copyAttributesAndChildren(Element source, Element target, String field, String content) {
    // Copy attributes
    NamedNodeMap attributes = source.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      Node attr = attributes.item(i);
      target.setAttribute(attr.getNodeName(), attr.getNodeValue());
    }

    // Set new content for the specified field
    target.setAttribute(field, content);

    // Copy child nodes
    NodeList children = source.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      Node copy = child.cloneNode(true);
      target.appendChild(copy);
    }
  }

  public static void zipDirectory(Path dirPath, Path zipFilePath) throws IOException {
    Path zipFile = Files.createFile(zipFilePath);
    try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
      Files.walk(dirPath)
          .filter(path -> !Files.isDirectory(path))
          .forEach(path -> {
            ZipEntry zipEntry = new ZipEntry(dirPath.relativize(path).toString().replace("\\", "/"));
            try {
              zs.putNextEntry(zipEntry);
              Files.copy(path, zs);
              zs.closeEntry();
            } catch (IOException e) {
              System.err.println(e);
            }
          });
    }
  }
}