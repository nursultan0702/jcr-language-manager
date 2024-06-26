package com.allianz;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLContentExtractor {
  private static final Set<String> IGNORED_FIELDS = new HashSet<>(Arrays.asList(
      "LastModified", "ContentType", "_type", "action", "alignment", "buttonType",
      "colour", "colourText", "componentAlignment", "date", "display", "email",
      "googleSiteVerificationCode", "icon", "iconAllowed", "iconDenied", "id",
      "imageAlignment", "jcr:mixinTypes", "jcr:primaryType", "jsonLd", "link",
      "marginBottom", "marginTop", "name", "orderDate", "redirect", "shapeAlignment",
      "size", "titleAlign", "titleAlignment", "titleSize", "viewDesktop", "viewMobile",
      "widgetName", "widgetType", "isButton", "nofollow", "noFollow", "isExternal", "hasNoFooter",
      "hasNoHeader", "footerBackground", "headerActiveCtaBackground", "mainBackground",
      "hasBreadcrumbs","alignTitle","borderTop","borderBottom","align", "isRegistration"
  ));
  private static final Pattern PATH_PATTERN =
      Pattern.compile("^/content/dam/onemarketing/allyz/allyz-com/cyber-care/.+");


  public static void main(String[] args) {
    String zipFilePath =
        "C:\\Users\\Nursultan Sadyk\\Desktop\\cc-master-lang.zip"; // Update this path
    String outputCsvPath = "C:\\Users\\Nursultan Sadyk\\Desktop\\output.csv"; // Update this path

    try {
      unzip(zipFilePath, "C:\\Users\\Nursultan Sadyk\\Desktop\\extractedFiles"); // Update this path
      List<String[]> csvData = extractContentToCsv(
          Paths.get("C:\\Users\\Nursultan Sadyk\\Desktop\\extractedFiles")); // Update this path
      writeCsv(outputCsvPath, csvData);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void unzip(String zipFilePath, String destDirectory) throws IOException {
    File destDir = new File(destDirectory);
    if (!destDir.exists()) {
      destDir.mkdir();
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

  public static List<String[]> extractContentToCsv(Path dirPath) throws Exception {
    List<String[]> csvData = new ArrayList<>();
    csvData.add(new String[] {"File", "Field", "Master", "FR", "DE", "NL", "IT"});

    Files.walk(dirPath)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".content.xml"))
        .forEach(path -> {
          try {
            File xmlFile = path.toFile();
            String relativePath = dirPath.relativize(path).toString();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList dataNodes = doc.getElementsByTagName("data");
            for (int i = 0; i < dataNodes.getLength(); i++) {
              Node dataNode = dataNodes.item(i);
              if (dataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element dataElement = (Element) dataNode;
                Map<String, Map<String, String>> langFieldValues = new HashMap<>();

                NodeList languageNodes = dataElement.getChildNodes();
                for (int j = 0; j < languageNodes.getLength(); j++) {
                  Node langNode = languageNodes.item(j);
                  if (langNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element langElement = (Element) langNode;
                    String language = langElement.getTagName();
                    NamedNodeMap attributes = langElement.getAttributes();
                    for (int k = 0; k < attributes.getLength(); k++) {
                      Node attr = attributes.item(k);
                      String fieldName = attr.getNodeName();
                      String fieldValue = attr.getNodeValue();
                      if (!shouldIgnoreField(fieldName) && !shouldIgnoreValue(fieldValue)) {
                        langFieldValues
                            .computeIfAbsent(fieldName, key -> new HashMap<>())
                            .put(language, fieldValue);
                      }
                    }
                  }
                }

                for (Map.Entry<String, Map<String, String>> entry : langFieldValues.entrySet()) {
                  String field = entry.getKey();
                  Map<String, String> values = entry.getValue();
                  csvData.add(new String[] {
                      relativePath,
                      field,
                      values.getOrDefault("master", ""),
                      values.getOrDefault("fr", ""),
                      values.getOrDefault("de", ""),
                      values.getOrDefault("nl", ""),
                      values.getOrDefault("it", "")
                  });
                }
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    return csvData;
  }

  public static boolean shouldIgnoreField(String fieldName) {
    return IGNORED_FIELDS.stream().anyMatch(fieldName::contains);
  }

  public static boolean shouldIgnoreValue(String fieldValue) {
    return PATH_PATTERN.matcher(fieldValue).matches();
  }

  public static void writeCsv(String filePath, List<String[]> data) throws IOException {
    CSVWriter writer = new CSVWriter(new FileWriter(filePath));
    writer.writeAll(data);
    writer.close();
  }
}