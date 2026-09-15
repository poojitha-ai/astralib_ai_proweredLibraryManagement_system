package com.astralib.backend;

import java.io.File;
import java.util.List;

public class EpubExtractionTest {

    public static void main(String[] args) {

        EpubTextExtractionService service =
                new EpubTextExtractionService();

        File epubFile =
                new File(
                        "C:\\Users\\DELL\\Desktop\\sdc2\\AstraLib\\data\\fulltext\\books\\sherlock_holmes\\sherlock_holmes.epub"
                );

        List<EpubTextExtractionService.EpubSection> sections =
                service.extractSections(epubFile);

        System.out.println();
        System.out.println("=================================");
        System.out.println("TOTAL DETECTED SECTIONS: "
                + sections.size());
        System.out.println("=================================");

        for (EpubTextExtractionService.EpubSection section : sections) {

            System.out.println(
                    section.getOrder()
                            + " -> "
                            + section.getTitle()
                            + " | chars="
                            + section.getContent().length()
            );
        }
    }
}