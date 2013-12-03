/*
 * Copyright (C) 2013 Google Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;

import com.google.clearsilver.jsilver.data.Data;

/**
* Metadata associated with a specific documentation page. Extracts
* metadata based on the page's declared hdf vars (meta.tags and others)
* as well as implicit data relating to the page, such as url, type, etc.
* Includes a Node class that represents the metadata and lets it attach
* to parent/child elements in the tree metadata nodes for all pages.
* Node also includes methods for rendering the node tree to a json file
* in docs output, which is then used by JavaScript to load metadata
* objects into html pages.
*/

public class PageMetadata {
  File mSource;
  String mDest;
  String mTagList;
  static boolean sLowercaseTags = true;
  static boolean sLowercaseKeywords = true;

  public PageMetadata(File source, String dest, List<Node> taglist) {
    mSource = source;
    mDest = dest;

    if (dest != null) {
      int len = dest.length();
      if (len > 1 && dest.charAt(len - 1) != '/') {
        mDest = dest + '/';
      } else {
        mDest = dest;
      }
    }
  }

  /**
  * Given a list of metadata nodes organized by type, sort the
  * root nodes by type name and render the types and their child
  * metadata nodes to a json file in the out dir.
  *
  * @param rootTypeNodesList A list of root metadata nodes, each
  *        representing a type and it's member child pages.
  */
  public static void WriteList(List<Node> rootTypeNodesList) {

    Collections.sort(rootTypeNodesList, BY_TYPE_NAME);
    Node pageMeta = new Node.Builder().setLabel("TOP").setChildren(rootTypeNodesList).build();

    StringBuilder buf = new StringBuilder();
    // write the taglist to string format
    pageMeta.renderTypeResources(buf);
    pageMeta.renderTypesByTag(buf);
    // write the taglist to js file
    Data data = Doclava.makeHDF();
    data.setValue("reference_tree", buf.toString());
    ClearPage.write(data, "jd_lists_unified.cs", "jd_lists_unified.js");
  }

  /**
  * Extract supported metadata values from a page and add them as
  * a child node of a root node based on type. Some metadata values
  * are normalized. Unsupported metadata fields are ignored. See
  * Node for supported metadata fields and methods for accessing values.
  *
  * @param file The file from which to extract metadata.
  * @param dest The output path for the file, used to set link to page.
  * @param filename The file from which to extract metadata.
  * @param hdf Data object in which to store the metadata values.
  * @param tagList The file from which to extract metadata.
  * @return tagList with new node added.
  */
  public static List<Node> setPageMetadata(File file, String dest, String filename,
      Data hdf, List<Node> tagList) {
    //exclude this page if author does not want it included
    boolean excludeNode = "true".equals(hdf.getValue("excludeFromSuggestions",""));
    if (!excludeNode) {
      Node pageMeta = new Node.Builder().build();
      pageMeta.setLabel(getTitleNormalized(hdf, "page.title"));
      pageMeta.setTitleFriendly(hdf.getValue("page.titleFriendly",""));
      pageMeta.setSummary(hdf.getValue("page.summary",""));
      pageMeta.setLink(filename);
      pageMeta.setGroup(getStringValueNormalized(hdf,"sample.group"));
      pageMeta.setKeywords(getPageTagsNormalized(hdf, "page.tags"));
      pageMeta.setTags(getPageTagsNormalized(hdf, "meta.tags"));
      pageMeta.setImage(getStringValueNormalized(hdf, "page.image"));
      pageMeta.setLang(getLangStringNormalized(filename));
      pageMeta.setType(getStringValueNormalized(hdf, "page.type"));
      appendMetaNodeByType(pageMeta, tagList);
    }
    return tagList;
  }

  /**
  * Normalize a comma-delimited, multi-string value. Split on commas, remove
  * quotes, trim whitespace, optionally make keywords/tags lowercase for
  * easier matching.
  *
  * @param hdf Data object in which the metadata values are stored.
  * @param tag The hdf var from which the metadata was extracted.
  * @return A normalized string value for the specified tag.
  */
  public static String getPageTagsNormalized(Data hdf, String tag) {

    StringBuilder tags = new StringBuilder();
    String tagList = hdf.getValue(tag, "");
    if (!tagList.equals("")) {
      tagList = tagList.replaceAll("\"", "");
      String[] tagParts = tagList.split(",");
      for (int iter = 0; iter < tagParts.length; iter++) {
        tags.append("'");
        if (tag.equals("meta.tags") && sLowercaseTags) {
          tagParts[iter] = tagParts[iter].toLowerCase();
        } else if (tag.equals("page.tags") && sLowercaseKeywords) {
          tagParts[iter] = tagParts[iter].toLowerCase();
        }
        tags.append(tagParts[iter].trim());
        tags.append("'");
        if (iter < tagParts.length - 1) {
          tags.append(",");
        }
      }
    }
    return tags.toString();
  }

  /**
  * Normalize a string for which only a single value is supported.
  * Extract the string up to the first comma, remove quotes, remove
  * any forward-slash prefix, trim any whitespace, optionally make
  * lowercase for easier matching.
  *
  * @param hdf Data object in which the metadata values are stored.
  * @param tag The hdf var from which the metadata should be extracted.
  * @return A normalized string value for the specified tag.
  */
  public static String getStringValueNormalized(Data hdf, String tag) {
    StringBuilder outString =  new StringBuilder();
    String tagList = hdf.getValue(tag, "");
    if (!tagList.isEmpty()) {
      tagList.replaceAll("\"", "");
      int end = tagList.indexOf(",");
      if (end != -1) {
        tagList = tagList.substring(0,end);
      }
      tagList = tagList.startsWith("/") ? tagList.substring(1) : tagList;
      if ("sample.group".equals(tag) && sLowercaseTags) {
        tagList = tagList.toLowerCase();
      }
      outString.append(tagList.trim());
    }
    return outString.toString();
  }

  /**
  * Normalize a page title. Extract the string, remove quotes, remove
  * markup, and trim any whitespace.
  *
  * @param hdf Data object in which the metadata values are stored.
  * @param tag The hdf var from which the metadata should be extracted.
  * @return A normalized string value for the specified tag.
  */
  public static String getTitleNormalized(Data hdf, String tag) {
    StringBuilder outTitle =  new StringBuilder();
    String title = hdf.getValue(tag, "");
    if (!title.isEmpty()) {
      title = title.replaceAll("\"", "'");
      if (title.indexOf("<span") != -1) {
        String[] splitTitle = title.split("<span(.*?)</span>");
        title = splitTitle[0];
        for (int j = 1; j < splitTitle.length; j++) {
          title.concat(splitTitle[j]);
        }
      }
      outTitle.append(title.trim());
    }
    return outTitle.toString();
  }

  /**
  * Extract and normalize a page's language string based on the
  * lowercased dir path. Non-supported langs are ignored and assigned
  * the default lang string of "en".
  *
  * @param filename A path string to the file relative to root.
  * @return A normalized lang value.
  */
  public static String getLangStringNormalized(String filename) {
    String[] stripStr = filename.toLowerCase().split("\\/");
    String outFrag = "en";
    if (stripStr.length > 0) {
      for (String t : DocFile.DEVSITE_VALID_LANGS) {
        if ("intl".equals(stripStr[0])) {
          if (t.equals(stripStr[1])) {
            outFrag = stripStr[1];
            break;
          }
        }
      }
    }
    return outFrag;
  }

  /**
  * Given a metadata node, add it as a child of a root node based on its
  * type. If there is no root node that matches the node's type, create one
  * and add the metadata node as a child node.
  *
  * @param gNode The node to attach to a root node or add as a new root node.
  * @param rootList The current list of root nodes.
  * @return The updated list of root nodes.
  */
  public static List<Node> appendMetaNodeByType(Node gNode, List<Node> rootList) {

    String nodeTags = gNode.getType();
    boolean matched = false;
    for (Node n : rootList) {
      if (n.getType().equals(nodeTags)) {  //find any matching type node
        n.getChildren().add(gNode);
        matched = true;
        break; // add to the first root node only
      } // tag did not match
    } // end rootnodes matching iterator
    if (!matched) {
      List<Node> mtaglist = new ArrayList<Node>(); // list of file objects that have a given type
      mtaglist.add(gNode);
      Node tnode = new Node.Builder().setChildren(mtaglist).setType(nodeTags).build();
      rootList.add(tnode);
    }
    return rootList;
  }

  /**
  * Given a metadata node, add it as a child of a root node based on its
  * tag. If there is no root node matching the tag, create one for it
  * and add the metadata node as a child node.
  *
  * @param gNode The node to attach to a root node or add as a new root node.
  * @param rootTagNodesList The current list of root nodes.
  * @return The updated list of root nodes.
  */
  public static List<Node> appendMetaNodeByTagIndex(Node gNode, List<Node> rootTagNodesList) {

    for (int iter = 0; iter < gNode.getChildren().size(); iter++) {
      if (gNode.getChildren().get(iter).getTags() != null) {
        List<String> nodeTags = gNode.getChildren().get(iter).getTags();
        boolean matched = false;
        for (String t : nodeTags) { //process each of the meta.tags
          for (Node n : rootTagNodesList) {
            if (n.getLabel().equals(t.toString())) {
              matched = true;
              break; // add to the first root node only
            } // tag did not match
          } // end rootnodes matching iterator
          if (!matched) {
            List<String> mtaglist = new ArrayList<String>(); // list of objects with a given tag
            mtaglist.add(String.valueOf(iter));
            Node tnode = new Node.Builder().setLabel(t.toString()).setTags(mtaglist).build();
            rootTagNodesList.add(tnode);
          }
        }
      }
    }
    return rootTagNodesList;
  }

  public static final Comparator<Node> BY_TAG_NAME = new Comparator<Node>() {
    public int compare (Node one, Node other) {
      return one.getLabel().compareTo(other.getLabel());
    }
  };

  public static final Comparator<Node> BY_TYPE_NAME = new Comparator<Node>() {
    public int compare (Node one, Node other) {
      return one.getType().compareTo(other.getType());
    }
  };

  /**
  * A node for storing page metadata. Use Builder.build() to instantiate.
  */
  public static class Node {

    private String mLabel; // holds page.title or similar identifier
    private String mTitleFriendly; // title for card or similar use
    private String mSummary; // Summary for card or similar use
    private String mLink; //link href for item click
    private String mGroup; // from sample.group in _index.jd
    private List<String> mKeywords; // from page.tags
    private List<String> mTags; // from meta.tags
    private String mImage; // holds an href, fully qualified or relative to root
    private List<Node> mChildren;
    private String mLang;
    private String mType; // can be file, dir, video show, announcement, etc.

    private Node(Builder builder) {
      mLabel = builder.mLabel;
      mTitleFriendly = builder.mTitleFriendly;
      mSummary = builder.mSummary;
      mLink = builder.mLink;
      mGroup = builder.mGroup;
      mKeywords = builder.mKeywords;
      mTags = builder.mTags;
      mImage = builder.mImage;
      mChildren = builder.mChildren;
      mLang = builder.mLang;
      mType = builder.mType;
    }

    private static class Builder {
      private String mLabel, mTitleFriendly, mSummary, mLink, mGroup, mImage, mLang, mType;
      private List<String> mKeywords = null;
      private List<String> mTags = null;
      private List<Node> mChildren = null;
      public Builder setLabel(String mLabel) { this.mLabel = mLabel; return this;}
      public Builder setTitleFriendly(String mTitleFriendly) {
        this.mTitleFriendly = mTitleFriendly; return this;
      }
      public Builder setSummary(String mSummary) {this.mSummary = mSummary; return this;}
      public Builder setLink(String mLink) {this.mLink = mLink; return this;}
      public Builder setGroup(String mGroup) {this.mGroup = mGroup; return this;}
      public Builder setKeywords(List<String> mKeywords) {
        this.mKeywords = mKeywords; return this;
      }
      public Builder setTags(List<String> mTags) {this.mTags = mTags; return this;}
      public Builder setImage(String mImage) {this.mImage = mImage; return this;}
      public Builder setChildren(List<Node> mChildren) {this.mChildren = mChildren; return this;}
      public Builder setLang(String mLang) {this.mLang = mLang; return this;}
      public Builder setType(String mType) {this.mType = mType; return this;}
      public Node build() {return new Node(this);}
    }

    /**
    * Render a tree of metadata nodes organized by type.
    * @param buf Output buffer to render to.
    */
    void renderTypeResources(StringBuilder buf) {
      List<Node> list = mChildren; //list of type rootnodes
      if (list == null || list.size() == 0) {
        buf.append("null");
      } else {
        final int n = list.size();
        for (int i = 0; i < n; i++) {
          buf.append("var " + list.get(i).mType.toUpperCase() + "_RESOURCES = [");
          list.get(i).renderTypes(buf); //render this type's children
          buf.append("\n];\n\n");
        }
      }
    }
    /**
    * Render all metadata nodes for a specific type.
    * @param buf Output buffer to render to.
    */
    void renderTypes(StringBuilder buf) {
      List<Node> list = mChildren;
      if (list == null || list.size() == 0) {
        buf.append("nulltype");
      } else {
        final int n = list.size();
        for (int i = 0; i < n; i++) {
          buf.append("\n      {\n");
          buf.append("        title:\"" + list.get(i).mLabel + "\",\n" );
          buf.append("        titleFriendly:\"" + list.get(i).mTitleFriendly + "\",\n" );
          buf.append("        summary:\"" + list.get(i).mSummary + "\",\n" );
          buf.append("        url:\"" + list.get(i).mLink + "\",\n" );
          buf.append("        group:\"" + list.get(i).mGroup + "\",\n" );
          list.get(i).renderArrayType(buf, list.get(i).mKeywords, "keywords");
          list.get(i).renderArrayType(buf, list.get(i).mTags, "tags");
          buf.append("        image:\"" + list.get(i).mImage + "\",\n" );
          buf.append("        lang:\"" + list.get(i).mLang + "\",\n" );
          buf.append("        type:\"" + list.get(i).mType + "\"");
          buf.append("\n      }");
          if (i != n - 1) {
            buf.append(", ");
          }
        }
      }
    }

    /**
    * Build and render a list of tags associated with each type.
    * @param buf Output buffer to render to.
    */
    void renderTypesByTag(StringBuilder buf) {
      List<Node> list = mChildren; //list of rootnodes
      if (list == null || list.size() == 0) {
        buf.append("null");
      } else {
        final int n = list.size();
        for (int i = 0; i < n; i++) {
        buf.append("var " + list.get(i).mType.toUpperCase() + "_BY_TAG = {");
        List<Node> mTagList = new ArrayList(); //list of rootnodes
        mTagList = appendMetaNodeByTagIndex(list.get(i), mTagList);
        list.get(i).renderTagIndices(buf, mTagList);
          buf.append("\n};\n\n");
        }
      }
    }

    /**
    * Render a list of tags associated with a type, including the
    * tag's indices in the type array.
    * @param buf Output buffer to render to.
    * @param tagList Node tree of types to render.
    */
    void renderTagIndices(StringBuilder buf, List<Node> tagList) {
      List<Node> list = tagList;
      if (list == null || list.size() == 0) {
        buf.append("null");
      } else {
        final int n = list.size();
        for (int i = 0; i < n; i++) {

          buf.append("\n    " + list.get(i).mLabel + ":[");
          renderArrayValue(buf, list.get(i).mTags);
          buf.append("]");
          if (i != n - 1) {
            buf.append(", ");
          }
        }
      }
    }

    /**
    * Render key:arrayvalue pair.
    * @param buf Output buffer to render to.
    * @param type The list value to render as an arrayvalue.
    * @param key The key for the pair.
    */
    void renderArrayType(StringBuilder buf, List<String> type, String key) {
      buf.append("        " + key + ": [");
      renderArrayValue(buf, type);
      buf.append("],\n");
    }

    /**
    * Render an array value to buf, with special handling of unicode characters.
    * @param buf Output buffer to render to.
    * @param type The list value to render as an arrayvalue.
    */
    void renderArrayValue(StringBuilder buf, List<String> type) {
      List<String> list = type;
      if (list != null) {
        final int n = list.size();
        for (int i = 0; i < n; i++) {
          String tagval = list.get(i).toString();
          final int L = tagval.length();
          for (int t = 0; t < L; t++) {
            char c = tagval.charAt(t);
            if (c >= ' ' && c <= '~' && c != '\\') {
              buf.append(c);
            } else {
              buf.append("\\u");
              for (int m = 0; m < 4; m++) {
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
          if (i != n - 1) {
            buf.append(",");
          }
        }
      }
    }

    public String getLabel() {
      return mLabel;
    }

    public void setLabel(String label) {
       mLabel = label;
    }

    public String getTitleFriendly() {
      return mTitleFriendly;
    }

    public void setTitleFriendly(String title) {
       mTitleFriendly = title;
    }

    public String getSummary() {
      return mSummary;
    }

    public void setSummary(String summary) {
       mSummary = summary;
    }

    public String getLink() {
      return mLink;
    }

    public void setLink(String ref) {
       mLink = ref;
    }

    public String getGroup() {
      return mGroup;
    }

    public void setGroup(String group) {
      mGroup = group;
    }

    public List<String> getTags() {
        return mTags;
    }

    public void setTags(String tags) {
      if ("".equals(tags)) {
        mTags = null;
      } else {
        List<String> tagList = new ArrayList();
        String[] tagParts = tags.split(",");

        for (String t : tagParts) {
          tagList.add(t);
        }
        mTags = tagList;
      }
    }

    public List<String> getKeywords() {
        return mKeywords;
    }

    public void setKeywords(String keywords) {
      if ("".equals(keywords)) {
        mKeywords = null;
      } else {
        List<String> keywordList = new ArrayList();
        String[] keywordParts = keywords.split(",");

        for (String k : keywordParts) {
          keywordList.add(k);
        }
        mKeywords = keywordList;
      }
    }

    public String getImage() {
        return mImage;
    }

    public void setImage(String ref) {
       mImage = ref;
    }

    public List<Node> getChildren() {
        return mChildren;
    }

    public void setChildren(List<Node> node) {
        mChildren = node;
    }

    public String getLang() {
      return mLang;
    }

    public void setLang(String lang) {
      mLang = lang;
    }

    public String getType() {
      return mType;
    }

    public void setType(String type) {
      mType = type;
    }
  }
}
