package org.apache.maven.model;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelVersion implements Comparable<ModelVersion>
{

    public static final ModelVersion
        V4_0_0 = new ModelVersion( 4, 0, 0 ),
        V4_1_0 = new ModelVersion( 4, 1, 0 );

    public static final List<ModelVersion> ALL_KNOWN_VERSIONS = Collections.unmodifiableList( Arrays.asList(
            V4_0_0,
            V4_1_0
    ) );

    public static final Pattern VERSION_PATTERN = Pattern.compile( "(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)" );

    private final int major, minor, patch;

    private final String stringRepresentation;

    public ModelVersion( int major, int minor, int patch )
    {
        this( major, minor, patch, major + "." + minor + "." + patch );
    }

    private ModelVersion( String stringRepresentation )
    {
        this( 0, 0, 0, stringRepresentation );
    }

    private ModelVersion( int major, int minor, int patch, String stringRepresentation )
    {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.stringRepresentation = stringRepresentation;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        ModelVersion that = ( ModelVersion ) o;

        if ( major != that.major )
        {
            return false;
        }
        if ( minor != that.minor )
        {
            return false;
        }
        return patch == that.patch;
    }

    @Override
    public int hashCode()
    {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        return result;
    }

    @Override
    public String toString()
    {
        return stringRepresentation;
    }

    @Override
    public int compareTo( ModelVersion o )
    {
        int result = Integer.compare( major, o.major );
        if ( result == 0 )
        {
            result = Integer.compare( minor, o.minor );
        }
        if ( result == 0 )
        {
            result = Integer.compare( patch, o.patch );
        }
        return result;
    }

    public static ModelVersion parse( String modelVersion )
    {
        if ( modelVersion == null )
        {
            return null;
        }
        modelVersion = modelVersion.trim();
        if ( modelVersion.isEmpty() )
        {
            return null;
        }
        Matcher m = VERSION_PATTERN.matcher( modelVersion );
        if ( m.matches() )
        {
            int major = Integer.parseInt( m.group( "major" ) );
            int minor = Integer.parseInt( m.group( "minor" ) );
            int patch = Integer.parseInt( m.group( "patch" ) );
            for ( int i = ALL_KNOWN_VERSIONS.size() - 1; i >= 0; i-- )
            {
                ModelVersion v = ALL_KNOWN_VERSIONS.get( i );
                if ( v.major == major && v.minor == minor && v.patch == patch )
                {
                    return v;
                }
            }
            return new ModelVersion( major, minor, patch, modelVersion );
        }
        return new ModelVersion( modelVersion );
    }
}
