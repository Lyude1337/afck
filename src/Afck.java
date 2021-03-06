/* (C) Copyright 2013 Stephen Chandler Paul (thatslyude@gmail.com)
 *
 * This program and accompanying materials are made available under the terms of
 * the GNU Lesser General Public License (LGPL) version 2.1 which accompanies
 * this distribution (LICENSE at the root of this project's directory), and is
 * also available at
 * http://www.gnu.org/licenses/gpl-3.0.html
 *
 * This program is distributed in the hopes that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.xml.parsers.*;
import org.xml.sax.SAXException;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.StreamResult;

public class Afck {

    public static final String VERSION = "0.0.0";

    // Buffer size for extracting Alice worlds
    public static final int BUFFER_SIZE = 2048;

    /**
     * Recursively returns all the elementData files in the directory
     * {@link parent}.
     *
     * @param parent The directory to search
     * @return       All the elementData files found
     */
    public static List<File> getAllElementData(File parent) {
        List<File> filteredList = new ArrayList<File>();
        for (File file : parent.listFiles()) {
            if (file.isDirectory())
                filteredList.addAll(getAllElementData(file));
            else if (file.getName().equals("elementData.xml"))
                filteredList.add(file);
        }
        return filteredList;
    }

    /**
     * Recursively look for an internal reference property for the index variable
     * in the elementData.xml file.
     *
     * @param rootElement The top element in the elementData.xml file
     * @return            Whether or not the property was found
     */
    public static boolean checkForIndexInternalReferenceMarker(Element rootElement) {
        NodeList elements = rootElement.getChildNodes();
        int elementCount = elements.getLength();
        for (int i = 0; i < elementCount; i++) {
            // Make sure the current item is actually an element
            if (elements.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element currentElement = (Element) elements.item(i);
                /* If the currentElement has children, check to see if we can find
                 * the property node for the index variable in them
                 */
                if (currentElement.hasChildNodes())
                    if (checkForIndexInternalReferenceMarker(currentElement))
                        return true;

                /* If the current element has a criterionClass, check if it marks
                 * index as an internal reference
                 */
                if (currentElement.hasAttribute("criterionClass")) {
                    if (currentElement.getAttribute("criterionClass").equals(
                        "edu.cmu.cs.stage3.alice.core.criterion.InternalReferenceKeyedCriterion") &&
                        currentElement.getFirstChild().getNodeValue().endsWith("index"))
                        return true;
                }
            }
        }

        // No index marker was found
        return false;
    }

    public static boolean hasIndexVariable(Element rootElement) {
        NodeList childFiles = rootElement.getElementsByTagName("child");
        int childFilesLength = childFiles.getLength();
        for (int i = 0; i < childFilesLength; i++) {
            Element currentElement = ((Element) childFiles.item(i));
            if (currentElement.getAttribute("filename").equals("index"))
                return true;
        }
        return false;
    }

    /**
     * Zips all the files in a directory
     * @param newZipFile    The new zip file to write to
     * @param inputDir      The directory to zip
     */
    public static void zipDirectory(File newZipFile, File inputDir) throws Exception {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(newZipFile));
        zipAddDir(inputDir, inputDir, out);
        out.close();
    }

    /** 
     * Internal method that recursively adds all the files in a directory to a
     * new zip file.
     * @param dir   The directory to zip
     * @param root  The top directory in the zip
     * @param out   The ZipOutputStream to use
     */
    private static void zipAddDir(File dir, File root, ZipOutputStream out) throws IOException {
        File[] files = dir.listFiles();
        byte[] outBuf = new byte[BUFFER_SIZE];

        // Add each file in the directory
        for (File currentFile : files) {
            // If we've stumbled upon a directory, recurse into it
            if (currentFile.isDirectory()) {
                zipAddDir(currentFile, root, out);
                continue;
            }

            FileInputStream in = new FileInputStream(currentFile.getAbsolutePath());
            // Remember, we don't want to store the full path in the zip file!
            out.putNextEntry(new ZipEntry(currentFile.getAbsolutePath().
                             replace(root.getAbsolutePath() + File.separator, "")));
            int len;
            while ((len = in.read(outBuf)) > 0) 
                out.write(outBuf, 0, len);

            out.closeEntry();
            in.close();
        }
    }


    public static void main(String[] args) {
        // LGPL stuff
        System.out.print("afck Copyright (C) 2013 Chandler Paul\n"             +
                         "This program comes with ABSOLUTELY NO WARRANTY "     +
                         "and is distributed under the terms of the LGPL "     +
                         "license. For more information, please see the file " +
                         "LICENSE at the root of the source code, or go to "   +
                         "http://www.gnu.org/licenses/lgpl.html\n");

        // Don't bother with the GUI if a path is specified on the command line
        if (args.length > 0) {
            File brokenAliceWorld = new File(args[0]);
            File tmpDir;
            
            /* Check to see if the main argument is a file name, otherwise
             * return an error
             */
            if (!brokenAliceWorld.exists()) {
                System.err.printf("Path specified is not valid! Exiting...\n");
                System.exit(-1);
            }

            // Create a temporary directory and extract the zip
            tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator +
                              "afck-" + Integer.toString(Math.abs(new Random().
                                                                  nextInt())));
            tmpDir.mkdirs();

            // Attempt to extract the alice world to the temporary directory
            try {
                ZipFile brokenAliceWorldZip = new ZipFile(brokenAliceWorld);
                Enumeration aliceZipEntries = brokenAliceWorldZip.entries();

                System.out.print("Extracting world...");

                // Extract each file in the Alice world
                while (aliceZipEntries.hasMoreElements()) {
                    // Get the current zip entry
                    ZipEntry entry = (ZipEntry) aliceZipEntries.nextElement();
                    String currentEntry = entry.getName();
                    File destFile = new File(tmpDir, currentEntry);

                    /* If the parent directory structure for the current entry needs
                     * to be created, do so
                     */
                    File destinationParent = destFile.getParentFile();
                    destinationParent.mkdirs();

                    // If the current entry is a file, begin writing it to the disk
                    if (!entry.isDirectory()) {
                        BufferedInputStream inputStream = new BufferedInputStream(brokenAliceWorldZip.getInputStream(entry));
                        int currentByte;
                        byte data[] = new byte[BUFFER_SIZE]; // Buffer

                        // Create output streams
                        FileOutputStream outputStream = new FileOutputStream(destFile);
                        BufferedOutputStream dest = new BufferedOutputStream(outputStream, BUFFER_SIZE);

                        // Read/Write until EOF
                        while ((currentByte = inputStream.read(data, 0, BUFFER_SIZE)) != -1)
                            dest.write(data, 0, currentByte);

                        // Flush the data
                        dest.flush();

                        // Close the streams
                        dest.close();
                        outputStream.close();
                        inputStream.close();
                    }
                }
            }
            catch (Exception e) {
                System.err.printf("Fatal exception! %s\n" +
                                  "Exiting...\n",
                                  e.getMessage());
                System.exit(-1);
            }

            System.out.print(" done!\n");

            /* Look through all the element data in the alice world and find
             * improperly marked index variables
             * Note: At some point I might convert this to the SAX parser, but
             * right now I'm on a deadline to get this in by it's due date, so
             * for now we'll just stick with DOM
             */
            try {
                System.out.print("Finding issues...");
                DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

                for (File file : getAllElementData(tmpDir)) {
                    Document elementData = docBuilder.parse(file);
                    Element rootElement = elementData.getDocumentElement();
                    
                    /* Make sure the index variable is marked correctly,
                     * otherwise fix it
                     */
                    if (hasIndexVariable (rootElement) && !checkForIndexInternalReferenceMarker(rootElement)) {
                        /* Find the original property element for index, and
                         * remove it to make space for the new correctly marked
                         * one
                         */
                        NodeList propertyNodes = rootElement.getElementsByTagName("property");
                        int propertyCount = propertyNodes.getLength();
                        Node badNode = null;
                        for (int i = 0; i < propertyCount; i++) {
                            Node currentNode = propertyNodes.item(i);
                            
                            /* If we've found the bad node, store it in the
                             * appropriate variable
                             */
                            if (((Element) currentNode).getAttribute("name").
                                equals("index")) {
                                badNode = currentNode;
                                break;
                            }
                        }

                        // Fix the bad node
                        ((Element) badNode).setAttribute("criterionClass",
                            "edu.cmu.cs.stage3.alice.core.criterion.InternalReferenceKeyedCriterion");
                        /* Determine the full class name of the index variable
                         * and add it to the bad node
                         */
                        ((Element) badNode).setTextContent(file.getAbsolutePath().
                            replace(tmpDir.getAbsolutePath() + File.separator, "").
                            replace("elementData.xml", "").
                            replace(File.separator, ".") + "index");
                        // Write to the elementData file
                        try {
                            TransformerFactory tFactory = TransformerFactory.newInstance();
                            Transformer transformer = tFactory.newTransformer();

                            DOMSource source = new DOMSource(elementData);
                            StreamResult result = new StreamResult(file);
                            transformer.transform(source, result);
                        }
                        catch (Exception e) {
                            System.err.printf("Fatal exception! %s\n" +
                                              "Exiting...\n",
                                              e.getMessage());
                            System.exit(-1);
                        }
                    }
                }
            }
            catch (Exception e) {
                System.err.printf("Fatal exception! %s\n" +
                                  "Exiting...\n",
                                  e.getMessage());
                System.exit(-1);
            }

            System.out.print(" done!\n");

            // Create the new world file for Alice
            System.out.print("Creating new world file...");
            try {
                zipDirectory(new File(args[1]), tmpDir);
            }
            catch (Exception e) {
                System.err.printf("Fatal exception! %s\n" +
                                  "Exiting...\n",
                                  e.getMessage());
                System.exit(-1);
            }

            System.out.print(" done!\n");

            // Clean up
            tmpDir.delete();
        }
    }
}
// vim: tw=80:expandtab:tabstop=4:softtabstop=4:shiftwidth=4
