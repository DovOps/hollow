/*
 *  Copyright 2021 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.hollow.api.codegen.testdata;

import com.netflix.hollow.core.HollowDataset;
import com.netflix.hollow.core.schema.HollowObjectSchema;
import com.netflix.hollow.core.schema.HollowObjectSchema.FieldType;
import com.netflix.hollow.core.schema.HollowSchema;
import com.netflix.hollow.core.schema.HollowSchema.SchemaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class HollowObjectTypeTestDataAPIClassGenerator {
    
    private final HollowDataset dataset;
    private final HollowObjectSchema schema;
    private final String packageName;
    private final String className;
    
    public HollowObjectTypeTestDataAPIClassGenerator(HollowDataset dataset, HollowObjectSchema schema, String packageName) {
        this.dataset = dataset;
        this.schema = schema;
        this.packageName = packageName;
        this.className = schema.getName() + "TestData";
    }
    
    public String generate() {
        StringBuilder builder = new StringBuilder();
        
        
        StringBuilder importBuilder = new StringBuilder();
        importBuilder.append("package ").append(packageName).append(";\n\n");
        
        importBuilder.append("import com.netflix.hollow.api.testdata.HollowTestObjectRecord;\n");
        if(schema.getPrimaryKey() != null) {
            importBuilder.append("import com.netflix.hollow.core.index.key.PrimaryKey;\n");
        }
        importBuilder.append("import com.netflix.hollow.core.schema.HollowObjectSchema;\n"); 
        importBuilder.append("import com.netflix.hollow.core.schema.HollowObjectSchema.FieldType;\n\n");
        
        Set<String> fieldTypesToImport = new HashSet<>();
        
        builder.append("public class ").append(className).append("<T> extends HollowTestObjectRecord<T> {\n\n");
        
        builder.append("    ").append(className).append("(T parent){\n");
        builder.append("        super(parent);\n");
        builder.append("    }\n\n");
        
        for(int i=0;i<schema.numFields();i++) {
            String fieldName = schema.getFieldName(i);
            switch(schema.getFieldType(i)) {
            case INT:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Integer ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case LONG:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Long ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case FLOAT:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Float ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case DOUBLE:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Double ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case BOOLEAN:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Boolean ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case BYTES:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(byte[] ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case STRING:
                builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(String ").append(fieldName).append(") {\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", ").append(fieldName).append(");\n");
                builder.append("        return this;\n");
                builder.append("    }\n\n");
                break;
            case REFERENCE:
                String refType = schema.getReferencedType(i);
                String returnType = className(refType) + "<" + className + "<T>>";
                builder.append("    public ").append(returnType).append(" ").append(fieldName).append("() {\n");
                builder.append("        ").append(returnType).append(" __x = new ").append(returnType).append("(this);\n");
                builder.append("        super.addField(\"").append(fieldName).append("\", __x);\n");
                builder.append("        return __x;\n");
                builder.append("    }\n\n");
                
                if(canErgonomicShortcut(i)) {
                    HollowObjectSchema refSchema = (HollowObjectSchema)dataset.getSchema(refType);
                    String refField = refSchema.getFieldName(0);
                    switch(refSchema.getFieldType(0)) {
                    case INT:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Integer ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case LONG:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Long ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case FLOAT:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Float ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case DOUBLE:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Double ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case BOOLEAN:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(Boolean ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case BYTES:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(byte[] ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    case STRING:
                        builder.append("    public ").append(className).append("<T> ").append(fieldName).append("(String ").append(fieldName).append(") {\n");
                        builder.append("        ").append(fieldName).append("().").append(refField).append("(").append(fieldName).append(");\n");
                        builder.append("        return this;\n");
                        builder.append("    }\n\n");
                        break;
                    default:
                        throw new IllegalStateException("Cannot actually ergonomic shortcut");
                    }

                }
            }
        }
        
        builder.append("    public static final HollowObjectSchema SCHEMA = new HollowObjectSchema(\"").append(schema.getName()).append("\", ").append(schema.numFields());
        if(schema.getPrimaryKey() != null) {
            builder.append(", new PrimaryKey(\"").append(schema.getName()).append("\"");
            
            for(int i=0;i<schema.getPrimaryKey().numFields();i++) {
                builder.append(", \"").append(schema.getPrimaryKey().getFieldPath(i)).append("\"");
            }
            
            builder.append(")");
        }
        
        builder.append(");\n\n");
        
        
        builder.append("    static {\n");
        for(int i=0;i<schema.numFields();i++) {
            builder.append("        SCHEMA.addField(\"").append(schema.getFieldName(i)).append("\", FieldType.").append(schema.getFieldType(i).name());
            if(schema.getFieldType(i) == FieldType.REFERENCE) {
                
                builder.append(", \"").append(schema.getReferencedType(i)).append("\"");
            }
            builder.append(");\n");
        }
        builder.append("    }\n\n");
        
        builder.append("    @Override public HollowObjectSchema getSchema() { return SCHEMA; }\n\n");
        
        builder.append("}");
        
        if(!fieldTypesToImport.isEmpty()) {
            List<String> fieldTypesList = new ArrayList<>(fieldTypesToImport);
            Collections.sort(fieldTypesList);
            for(String fieldType : fieldTypesList) {
                importBuilder.append("import ").append(packageName).append(".").append(className(fieldType)).append(".").append(fieldType).append("Field;\n");
            }
            importBuilder.append("\n");
        }
        
        return importBuilder.toString() + builder.toString();
    }
    
    public String className(String type) {
        return type + "TestData";
    }
    
    public boolean canErgonomicShortcut(int fieldIdx) {
        if(schema.getFieldType(fieldIdx) != FieldType.REFERENCE) {
            return false;
        }
        
        String refType = schema.getReferencedType(fieldIdx);
        HollowSchema refSchema = dataset.getSchema(refType);

        return canErgonomicShortcut(refSchema);
    }
    
    public boolean canErgonomicShortcut(HollowSchema schema) {
        if(schema.getSchemaType() != SchemaType.OBJECT) {
            return false;
        }
        
        HollowObjectSchema objSchema = (HollowObjectSchema)schema;

        if(objSchema.numFields() != 1) {
            return false;
        }
        
        return objSchema.getFieldType(0) != FieldType.REFERENCE;
    }
    
}
