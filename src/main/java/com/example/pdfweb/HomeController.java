package com.example.pdfweb;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.core.io.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@Controller
public class HomeController {

    private final String uploadDir = System.getProperty("user.dir") + "/uploads/";

    public HomeController() {
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    // ================= COMPRESS =================

    @GetMapping("/compress")
    public String compressPage() {
        return "compress";
    }

@PostMapping("/compress")
public String compress(@RequestParam("file") MultipartFile file,
                       @RequestParam("level") String level,
                       Model model) {

    try {

        File originalFile = new File(uploadDir + file.getOriginalFilename());
        file.transferTo(originalFile);

        long originalSize = originalFile.length() / 1024;

        PDDocument document = PDDocument.load(originalFile);

        float quality;

        // Adjust compression strength
        if (level.equals("low")) {
            quality = 0.3f;   // strong compression
        } else if (level.equals("medium")) {
            quality = 0.6f;
        } else {
            quality = 0.9f;   // high quality
        }

        for (PDPage page : document.getPages()) {
            page.getResources().getXObjectNames().forEach(name -> {
                try {
                    if (page.getResources().isImageXObject(name)) {
                        var image = page.getResources().getXObject(name);
                        if (image instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject img) {

                            var bufferedImage = img.getImage();

                            // Downscale image
                            int newWidth = (int)(bufferedImage.getWidth() * quality);
                            int newHeight = (int)(bufferedImage.getHeight() * quality);

                            java.awt.Image tmp = bufferedImage.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
                            java.awt.image.BufferedImage resized =
                                    new java.awt.image.BufferedImage(newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

                            java.awt.Graphics2D g2d = resized.createGraphics();
                            g2d.drawImage(tmp, 0, 0, null);
                            g2d.dispose();

                            var newImage = org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(document, resized);
                            page.getResources().put(name, newImage);
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        String compressedName = "compressed_" + System.currentTimeMillis() + ".pdf";
        File compressedFile = new File(uploadDir + compressedName);

        document.save(compressedFile);
        document.close();

        long compressedSize = compressedFile.length() / 1024;

        model.addAttribute("message", "Compression Completed");
        model.addAttribute("originalSize", originalSize + " KB");
        model.addAttribute("compressedSize", compressedSize + " KB");
        model.addAttribute("fileName", compressedName);

    } catch (Exception e) {
        model.addAttribute("message", "Error: " + e.getMessage());
    }

    return "result";
}

    // ================= MERGE =================

    @GetMapping("/merge")
    public String mergePage() {
        return "merge";
    }

    @PostMapping("/merge")
    public String merge(@RequestParam("file1") MultipartFile file1,
                        @RequestParam("file2") MultipartFile file2,
                        Model model) {

        try {
            PDFMergerUtility merger = new PDFMergerUtility();

            File f1 = new File(uploadDir + "file1.pdf");
            File f2 = new File(uploadDir + "file2.pdf");

            file1.transferTo(f1);
            file2.transferTo(f2);

            merger.addSource(f1);
            merger.addSource(f2);

            String mergedName = "merged_" + System.currentTimeMillis() + ".pdf";
            merger.setDestinationFileName(uploadDir + mergedName);
            merger.mergeDocuments(null);

            model.addAttribute("message", "Merge Completed");
            model.addAttribute("fileName", mergedName);

        } catch (Exception e) {
            model.addAttribute("message", "Error: " + e.getMessage());
        }

        return "result";
    }

    // ================= RESIZE (CROP) =================

    @GetMapping("/resize")
    public String resizePage() {
        return "resize";
    }

    @PostMapping("/resize")
    public String resize(@RequestParam("file") MultipartFile file,
                         @RequestParam("width") float width,
                         @RequestParam("height") float height,
                         Model model) {

        try {
            File originalFile = new File(uploadDir + file.getOriginalFilename());
            file.transferTo(originalFile);

            PDDocument document = PDDocument.load(originalFile);

            for (PDPage page : document.getPages()) {
                page.setCropBox(new PDRectangle(width, height));
            }

            String resizedName = "cropped_" + System.currentTimeMillis() + ".pdf";
            document.save(uploadDir + resizedName);
            document.close();

            model.addAttribute("message", "Resize Completed");
            model.addAttribute("fileName", resizedName);

        } catch (Exception e) {
            model.addAttribute("message", "Error: " + e.getMessage());
        }

        return "result";
    }

    // ================= SPLIT =================

    @GetMapping("/split")
    public String splitPage() {
        return "split";
    }

    @PostMapping("/split")
    public String split(@RequestParam("file") MultipartFile file,
                        @RequestParam("page") int page,
                        Model model) {

        try {
            File originalFile = new File(uploadDir + file.getOriginalFilename());
            file.transferTo(originalFile);

            PDDocument document = PDDocument.load(originalFile);

            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(page);

            List<PDDocument> docs = splitter.split(document);

            String firstName = "firstPart_" + System.currentTimeMillis() + ".pdf";
            String secondName = "remaining_" + System.currentTimeMillis() + ".pdf";

            docs.get(0).save(uploadDir + firstName);
            docs.get(1).save(uploadDir + secondName);

            document.close();

            model.addAttribute("message", "Split Completed");
            model.addAttribute("file1", firstName);
            model.addAttribute("file2", secondName);

        } catch (Exception e) {
            model.addAttribute("message", "Error: " + e.getMessage());
        }

        return "splitResult";
    }

    // ================= DOWNLOAD =================

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws Exception {

        File file = new File(uploadDir + filename);
        Resource resource = new UrlResource(file.toURI());

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}