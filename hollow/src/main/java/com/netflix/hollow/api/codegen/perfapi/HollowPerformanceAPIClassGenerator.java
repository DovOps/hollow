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
package com.netflix.hollow.api.codegen.perfapi;

import com.netflix.hollow.core.HollowDataset;
import com.netflix.hollow.core.schema.HollowSchema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class HollowPerformanceAPIClassGenerator {

    private final HollowDataset dataset;
    private final String apiClassName;
    private final String packageName;

    public HollowPerformanceAPIClassGenerator(HollowDataset dataset, String apiClassName, String packageName) {
        this.dataset = dataset;
        this.apiClassName = apiClassName;
        this.packageName = packageName;
    }

    public String generate() {
        StringBuilder builder = new StringBuilder();

        builder.append("package ").append(packageName).append(";\n\n");

        builder.append("import com.netflix.hollow.api.perfapi.HollowListTypePerfAPI;\n" +
                "import com.netflix.hollow.api.perfapi.HollowMapTypePerfAPI;\n" +
                "import com.netflix.hollow.api.perfapi.HollowPerformanceAPI;\n" +
                "import com.netflix.hollow.api.perfapi.HollowSetTypePerfAPI;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowDataAccess;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowListTypeDataAccess;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowMapTypeDataAccess;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowObjectTypeDataAccess;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowSetTypeDataAccess;\n" +
                "import com.netflix.hollow.core.read.dataaccess.HollowTypeDataAccess;\n" +
                "import java.util.Set;\n" +
                "\n");

        builder.append("@SuppressWarnings(\"all\")\n");
        builder.append("public class ").append(apiClassName).append(" extends HollowPerformanceAPI {\n\n");

        List<HollowSchema> schemas = new ArrayList<>(dataset.getSchemas());
        schemas.sort(Comparator.comparing(HollowSchema::getName));

        for(HollowSchema schema : schemas) {
            String schemaName = schema.getName();

            switch(schema.getSchemaType()) {
                case OBJECT:
                    builder.append("    public final ").append(schemaName).append("PerfAPI ").append(schemaName).append(";\n");
                    break;
                case LIST:
                    builder.append("    public final HollowListTypePerfAPI ").append(schemaName).append(";\n");
                    break;
                case SET:
                    builder.append("    public final HollowSetTypePerfAPI ").append(schemaName).append(";\n");
                    break;
                case MAP:
                    builder.append("    public final HollowMapTypePerfAPI ").append(schemaName).append(";\n");
                    break;
            }
        }

        builder.append("\n");

        builder.append("    public ").append(apiClassName).append("(HollowDataAccess dataAccess) {\n");
        builder.append("        super(dataAccess);\n\n");

        for(HollowSchema schema : schemas) {
            String schemaName = schema.getName();

            switch (schema.getSchemaType()) {
                case OBJECT:
                    builder.append("        this.").append(schemaName).append(" = new ").append(schemaName).append("PerfAPI(dataAccess, \"").append(schemaName).append("\", this);\n");
                    break;
                case LIST:
                    builder.append("        this.").append(schemaName).append(" = new HollowListTypePerfAPI(dataAccess, \"").append(schemaName).append("\", this);\n");
                    break;
                case MAP:
                    builder.append("        this.").append(schemaName).append(" = new HollowMapTypePerfAPI(dataAccess, \"").append(schemaName).append("\",  this);\n");
                    break;
                case SET:
                    builder.append("        this.").append(schemaName).append(" = new HollowSetTypePerfAPI(dataAccess, \"").append(schemaName).append("\",  this);\n");
                    break;
            }
        }

        builder.append("    }\n");

        builder.append("}");

        return builder.toString();
    }
}