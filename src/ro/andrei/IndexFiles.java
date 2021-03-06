/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package ro.andrei;


import java.io.*;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 * <p>
 * http://lucene.apache.org/core/6_0_1/demo/overview-summary.html#overview_description
 * https://wiki.apache.org/lucene-java/LuceneFAQ
 * https://wiki.apache.org/lucene-java/LuceneFAQ#How_can_I_index_PDF_documents.3F
 * http://tika.apache.org/
 */
public class IndexFiles
{

    public static final String DOCUMENTS_LOCATION = "C:\\Users\\covala\\Desktop\\Andrei HPE\\Masterat FMI\\II sem\\Regasirea Informatiei\\lucene\\index-src";
    public static final String INDEX_LOCATION = "C:\\Users\\covala\\Desktop\\Andrei HPE\\Masterat FMI\\II sem\\Regasirea Informatiei\\lucene\\index";

    private IndexFiles()
    {
    }

    public static void main(String[] args) throws IOException {

//        setam locatiile cu care vom lucra
        File docs     = new File(DOCUMENTS_LOCATION);
        File indexDir = new File(INDEX_LOCATION);

        System.out.println("Starting indexing documents location");
        System.out.println(" --> " + DOCUMENTS_LOCATION);

        System.out.println("New index is going to be located here");
        System.out.println(" --> " + INDEX_LOCATION);
        System.out.println("");

//        deschidem directoriul sursa
        Directory directory = FSDirectory.open(indexDir.toPath());

//        creem si configuram writerul de index
        Analyzer analyzer = new RomanianAnalyzer();
        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(directory, conf);

//        stergem valorile vechi
        writer.deleteAll();

        System.out.println("Indexing documents:");

//        pentru toate fisierele gasite in directoriul cu documente
//        executam operatiile de mai jos, si anume indexarea
        for (File file : docs.listFiles()) {

//            instantiem obiectele Tika si parserele care vor parsa si analiza documentele
            Metadata       metadata = new Metadata();
            ContentHandler handler  = new BodyContentHandler(){
                @Override
                public void startElement(String uri, String localName, String name, Attributes atts) throws SAXException {
                    super.startElement(uri, localName, name, atts);
                }
            };

            ParseContext   context  = new ParseContext();

//            foloseste un parser care detecteaza automat tipul documentului, din Tika
            Parser         parser   = new AutoDetectParser();

            InputStream    stream   = new FileInputStream(file);

            System.out.println("\n --> " + file.getName());

//  incercam sa parsam documentul curent, deschidem, citim si analizam
            try {
                parser.parse(stream, handler, metadata, context);
            }
            catch (TikaException | SAXException e) {
                e.printStackTrace();
            }
            finally {
                stream.close();
            }

            String text = handler.toString();
            String fileName = file.getName();

            // make a new, empty lucene document for each physical file
            Document doc = new Document();

//            in urmatoarele linii vom salva informatii despre document in diferite field-uri

            // Add the path of the file as a field named "path".  Use a
            // field that is indexed (i.e. searchable), but don't tokenize
            // the field into separate words and don't index term frequency
            // or positional information:
            doc.add(new TextField("path", fileName, Field.Store.YES));


            for (String key : metadata.names()) {
                String name = key.toLowerCase();
                String value = metadata.get(key);

                if (StringUtils.isBlank(value)) {
                    continue;
                }

                if ("keywords".equalsIgnoreCase(key)) {
                    for (String keyword : value.split(",?(\\s+)")) {
                        doc.add(new StringField(name, keyword, Field.Store.YES));
                    }
                }
                else if ("title".equalsIgnoreCase(key)) {
                    System.out.println(" -- --> title: " + value);
                    TextField titleField = new TextField(name, value, Field.Store.YES);
//                    acest boosting va afecta in mod implicit scoringul din rezultate cautarii
//                    pentru-ca noi consideram valorile din title ca fiind cu importanta mai mare
                    titleField.setBoost(titleField.boost() * 3);
                    doc.add(titleField);
                }
                else {
                    doc.add(new StringField(name, value, Field.Store.YES));
                }
            }

            // Add the contents of the file to a field named "contents".  Specify a Reader,
            // so that the text of the file is tokenized and indexed, but not stored.
            // Note that FileReader expects the file to be in UTF-8 encoding.
            // If that's not the case searching for special characters will fail.
            TextField contents = new TextField("contents", text, Field.Store.NO);
//            aici marim importanta cuvintelor din continut
            contents.setBoost(contents.boost() * 2);
            doc.add(contents);

            // New index, so we just add the document (no old document can be there):
            writer.addDocument(doc);

        }

//         commit changes so they become available for reading
        writer.commit();
        writer.deleteUnusedFiles();

        System.out.println(writer.maxDoc() + " documents written");
    }
}
