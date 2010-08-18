/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doclava;

import com.google.clearsilver.jsilver.data.Data;

import java.util.*;
import java.io.*;


public class SampleCode {
  String mSource;
  String mDest;
  String mTitle;

  public SampleCode(String source, String dest, String title) {
    mSource = source;
    mTitle = title;

    int len = dest.length();
    if (len > 1 && dest.charAt(len - 1) != '/') {
      mDest = dest + '/';
    } else {
      mDest = dest;
    }

    //System.out.println("SampleCode init: source: " + mSource);
    //System.out.println("SampleCode init: dest: " + mDest);
    //System.out.println("SampleCode init: title: " + mTitle);

  }

  public Node write(boolean offlineMode) {
    List<Node> filelist = new ArrayList<Node>();
    File f = new File(mSource);
    String name = f.getName();
    String startname = name;
    String subdir = mDest;
    String mOut = subdir + name;
    if (!f.isDirectory()) {
      System.out.println("-samplecode not a directory: " + mSource);
      return null;
    }

    if (offlineMode)
      writeIndexOnly(f, mDest, offlineMode);
    else {
      Data hdf = Doclava.makeHDF();
      hdf.setValue("samples", "true");
      writeProjectDirectory(filelist, f, mDest, false, hdf, "Files.");
      // values for handling breadcrumb for project.html file
      hdf.setValue("page.title", "Project Structure");
      hdf.removeTree("parentdirs");
      hdf.setValue("parentdirs.0.Name", name);
      hdf.setValue("showProjectPaths","true");
      hdf.setValue("samples", "true");
      ClearPage.write(hdf, "sampleindex.cs", mDest + "project" + Doclava.htmlExtension); //write the project.html file
      // return a root SC node for the sample with children appended
      return new Node(mTitle, "samples/" + startname + "/index.html", filelist, null);
    }
    return null;
  }

  public static String convertExtension(String s, String ext) {
    return s.substring(0, s.lastIndexOf('.')) + ext;
  }

  public static String[] IMAGES = {".png", ".jpg", ".gif"};
  public static String[] TEMPLATED = {".java", ".xml", ".aidl", ".rs",".txt", ".TXT"};

  public static boolean inList(String s, String[] list) {
    for (String t : list) {
      if (s.endsWith(t)) {
        return true;
      }
    }
    return false;
  }

  public static String mapTypes(String name) {
    String type = name.substring(name.lastIndexOf('.') + 1, name.length());
    if (type.equals("xml") || type.equals("java")) {
      if (name.equals("AndroidManifest.xml")) type = "manifest";
      return type;
    } else {
      return type = "file";
    }
  }

  public void writeProjectDirectory(List<Node> parent, File dir, String relative, Boolean recursed, Data hdf, String newkey) {
    TreeSet<String> dirs = new TreeSet<String>(); //dirs for project structure and breadcrumb
    TreeSet<String> files = new TreeSet<String>(); //files for project structure and breadcrumb

    String subdir = relative;
    String name = "";
    String label = "";
    String link = "";
    String type = "";
    int i = 0;
    String expansion = ".Sub.";
    String key = newkey;

    if (recursed) {
      key = (key + expansion);
    } else {
      expansion = "";
    }

    for (File f: dir.listFiles()) {
      name = f.getName();
      // don't process certain types of files
      if (name.startsWith(".") ||
          name.startsWith("_") ||
          name.equals("default.properties") ||
          name.equals("build.properties") ||
          name.endsWith(".ttf") ||
          name.equals("Android.mk")) {
         //System.out.println("Invalid File Type, bypassing: " + name);
         continue;
       }
       if (f.isFile() && name.contains(".")){
         String path = relative + name;
         type = mapTypes(name);
         link = convertExtension(path, ".html");
         hdf.setValue("samples", "true");//dd needed?

         if (inList(path, IMAGES)) {
           // copy these files to output directly
           type = "img";
           ClearPage.copyFile(false, f, path);
           writeImagePage(f, convertExtension(path, Doclava.htmlExtension), relative);
           files.add(name);
           hdf.setValue(key + i + ".Type", "img");
           hdf.setValue(key + i + ".Name", name);
           hdf.setValue(key + i + ".Href", link);
         }
         if (inList(path, TEMPLATED)) {
           // copied and goes through the template
           ClearPage.copyFile(false, f, path);
           writePage(f, convertExtension(path, Doclava.htmlExtension), relative);
           files.add(name);
           hdf.setValue(key + i + ".Type", type);
           hdf.setValue(key + i + ".Name", name);
           hdf.setValue(key + i + ".Href", link);
         }
         // add file to the navtree
         parent.add(new Node(name, link , null, type));
         i++;
       } else if (f.isDirectory()) {
         List<Node> mchildren = new ArrayList<Node>();
         type = "dir";
         String dirpath = relative + name;
         link = dirpath + "/index.html";
         String hdfkeyName = (key + i + ".Name");
         String hdfkeyType = (key + i + ".Type");
         String hdfkeyHref = (key + i + ".Href");
         hdf.setValue(hdfkeyName, name);
         hdf.setValue(hdfkeyType, type);
         hdf.setValue(hdfkeyHref, relative + name + "/" + "index.html");
         //System.out.println("Found directory, recursing. Current key: " + hdfkeyName);
         writeProjectDirectory(mchildren, f, relative + name + "/", true, hdf, (key + i));
         if (mchildren.size() > 0) {
           //dir is processed, now add it to the navtree
           //don't link sidenav subdirs at this point (but cab use "link" to do so)
          parent.add(new Node(name, null, mchildren, type));
         }
         //dirs.add(name); //dd not used?
         i++;
       }
    }
    //dd not working yet
    getSummaryFromDir(hdf, dir, newkey);
    hdf.setValue("resType", "Sample Code");
    hdf.setValue("resTag", "sample");
    hdf.setValue("showProjectPaths","false");
    //If this is an index for the project root (assumed root if split length is 3 (development/samples/nn)),
    //then remove the root dir so that it won't appear in the breadcrumb. Else just pass it through to
    //setParentDirs as usual.
    String mpath = dir + "";
    String sdir[] = mpath.split("/");
    if (sdir.length == 3 ) {
      System.out.println("-----------------> this must be the root: [sdir len]" + sdir.length + "[dir]" + dir);
      hdf.setValue("showProjectPaths","true");
    }
    setParentDirs(hdf, relative, name, false);
    System.out.println("writing  sample index -- " + relative + "index" + Doclava.htmlExtension);
    ClearPage.write(hdf, "sampleindex.cs", relative + "/index" + Doclava.htmlExtension);
  }

  public void writeDirectory(File dir, String relative, boolean offline) {
    TreeSet<String> dirs = new TreeSet<String>();
    TreeSet<String> files = new TreeSet<String>();

    String subdir = relative; // .substring(mDest.length());

    for (File f : dir.listFiles()) {
      String name = f.getName();
      if (name.startsWith(".") || name.startsWith("_")) {
        continue;
      }
      if (f.isFile()) {
        String out = relative + name;
        if (inList(out, IMAGES)) {
          // copied directly
          ClearPage.copyFile(false, f, out);
          writeImagePage(f, convertExtension(out, Doclava.htmlExtension), subdir);
          files.add(name);
        }
        if (inList(out, TEMPLATED)) {
          // copied and goes through the template
          ClearPage.copyFile(false, f, out);
          writePage(f, convertExtension(out, Doclava.htmlExtension), subdir);
          files.add(name);

        }
        // else ignored
      } else if (f.isDirectory()) {
        writeDirectory(f, relative + name + "/", offline);
        dirs.add(name);
      }
    }

    // write the index page
    int i;

    Data hdf = writeIndex(dir);
    hdf.setValue("subdir", subdir);
    i = 0;
    for (String d : dirs) {
      hdf.setValue("subdirs." + i + ".Name", d);
      hdf.setValue("files." + i + ".Href", convertExtension(d, ".html"));
      i++;
    }
    i = 0;
    for (String f : files) {
      hdf.setValue("files." + i + ".Name", f);
      hdf.setValue("files." + i + ".Href", convertExtension(f, ".html"));
      i++;
    }

    if (!offline) relative = "/" + relative;
    ClearPage.write(hdf, "sampleindex.cs", relative + "index" + Doclava.htmlExtension);
  }

  public void writeIndexOnly(File dir, String relative, Boolean offline) {
    Data hdf = writeIndex(dir);
    if (!offline) relative = "/" + relative;

      System.out.println("writing indexonly at " + relative + "/index" + Doclava.htmlExtension);
      ClearPage.write(hdf, "sampleindex.cs", relative + "index" + Doclava.htmlExtension);
  }

  public Data writeIndex(File dir) {
    Data hdf = Doclava.makeHDF();

    hdf.setValue("page.title", dir.getName() + " - " + mTitle);
    hdf.setValue("projectTitle", mTitle);

    String filename = dir.getPath() + "/_index.html";
    String summary =
        SampleTagInfo.readFile(new SourcePositionInfo(filename, -1, -1), filename, "sample code",
            true, false, false, true);

    if (summary == null) {
      summary = "";
    }
    hdf.setValue("summary", summary);

    return hdf;
  }

//dd START reformat this
    public Boolean getSummaryFromDir(Data hdf, File dir, String key) {
        hdf.setValue("page.title", dir.getName());
        hdf.setValue("projectTitle", mTitle);

        String filename = dir.getPath() + "/_index.html";
        String summary = SampleTagInfo.readFile(new SourcePositionInfo(filename,
                          -1,-1), filename, "sample code", true, false, false, true);

        if (summary != null) {
            hdf.setValue(key + "SummaryFlag", "true");
            hdf.setValue("summary", summary);
            //set the target for [info] link
            hdf.setValue(key + "Href", dir + "/index.html");
            return true;
        }
        else {
            hdf.setValue("summary", "");
            return false;
        }
    }

    Data setParentDirs(Data hdf, String subdir, String name, Boolean isFile) {
        isFile = false;
        int iter;
        hdf.removeTree("parentdirs");
        //System.out.println("setParentDirs for " + subdir + name);
        String s = subdir;
        String urlParts[] = s.split("/");
        int n, l = (isFile)?1:0;
        for (iter=2; iter < urlParts.length - l; iter++) {
            n = iter-2;
         //System.out.println("parentdirs." + n + ".Name == " + urlParts[iter]);
                hdf.setValue("parentdirs." + n + ".Name", urlParts[iter]);
            }
        return hdf;
    }//dd END reformat this









  public void writePage(File f, String out, String subdir) {
    String name = f.getName();
    String path = f.getPath();
    String data =
        SampleTagInfo.readFile(new SourcePositionInfo(path, -1, -1), path, "sample code",
            true, true, true, true);
    data = Doclava.escape(data);

    Data hdf = Doclava.makeHDF();
    hdf.setValue("samples", "true");
    setParentDirs(hdf, subdir, name, true);
    hdf.setValue("projectTitle", mTitle);
    hdf.setValue("page.title", name);
    hdf.setValue("subdir", subdir);
    hdf.setValue("realFile", name);
    hdf.setValue("fileContents", data);
    hdf.setValue("resTag", "sample");
    hdf.setValue("resType", "Sample Code");

    ClearPage.write(hdf, "sample.cs", out);
  }

  public void writeImagePage(File f, String out, String subdir) {
    String name = f.getName();

    String data = "<img src=\"" + name + "\" title=\"" + name + "\" />";

    Data hdf = Doclava.makeHDF();
    hdf.setValue("samples", "true");
    setParentDirs(hdf, subdir, name, true);
    hdf.setValue("page.title", name);
    hdf.setValue("projectTitle", mTitle);
    hdf.setValue("subdir", subdir);
    hdf.setValue("realFile", name);
    hdf.setValue("fileContents", data);
    hdf.setValue("resTag", "sample");
    hdf.setValue("resType", "Sample Code");
    ClearPage.write(hdf, "sample.cs", out);
  }

  /**
  * Render a SC node to a navtree js file.
  */
  public static void writeSamplesNavTree(List<Node> tnode) {
    Node node = new Node("Reference", "packages.html", tnode, null);

    StringBuilder buf = new StringBuilder();
    if (false) {
    // if you want a root node
      buf.append("[");
      node.render(buf);
      buf.append("]");
    } else {
      // if you don't want a root node
      node.renderChildren(buf);
    }

    Data data = Doclava.makeHDF();
    data.setValue("reference_tree", buf.toString());
    ClearPage.write(data, "samples_navtree_data.cs", "samples_navtree_data.js");
  }

  /**
  * SampleCode variant of NavTree node.
  */
  public static class Node {
    private String mLabel;
    private String mLink;
    List<Node> mChildren;
    private String mType;

    Node(String label, String link, List<Node> children, String type) {
      mLabel = label;
      mLink = link;
      mChildren = children;
      mType = type;
    }

    static void renderString(StringBuilder buf, String s) {
      if (s == null) {
        buf.append("null");
      } else {
        buf.append('"');
        final int N = s.length();
        for (int i = 0; i < N; i++) {
          char c = s.charAt(i);
          if (c >= ' ' && c <= '~' && c != '"' && c != '\\') {
            buf.append(c);
          } else {
            buf.append("\\u");
            for (int j = 0; i < 4; i++) {
              char x = (char) (c & 0x000f);
              if (x > 10) {
                x = (char) (x - 10 + 'a');
              } else {
                x = (char) (x + '0');
              }
              buf.append(x);
              c >>= 4;
            }
          }
        }
        buf.append('"');
      }
    }

    void renderChildren(StringBuilder buf) {
      List<Node> list = mChildren;
      if (list == null || list.size() == 0) {
        // We output null for no children. That way empty lists here can just
        // be a byproduct of how we generate the lists.
        buf.append("null");
      } else {
        buf.append("[ ");
        final int N = list.size();
        for (int i = 0; i < N; i++) {
          list.get(i).render(buf);
          if (i != N - 1) {
            buf.append(", ");
          }
        }
        buf.append(" ]\n");
      }
    }

    void render(StringBuilder buf) {
      buf.append("[ ");
      renderString(buf, mLabel);
      buf.append(", ");
      renderString(buf, mLink);
      buf.append(", ");
      renderChildren(buf);
      buf.append(", ");
      renderString(buf, mType);
      buf.append(" ]");
    }
  }

}
