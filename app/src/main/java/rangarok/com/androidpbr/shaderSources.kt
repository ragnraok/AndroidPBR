package rangarok.com.androidpbr

import org.intellij.lang.annotations.Language

@Language("glsl")
const val PbrVs = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoords;
    layout (location = 2) in vec3 aNormal;
    
    out vec2 TexCoords;
    out vec3 WorldPos;
    out vec3 Normal;
    
    uniform mat4 model;
    uniform mat4 view;
    uniform mat4 projection;
    
    void main()
    {
        TexCoords = aTexCoords;
        WorldPos = vec3(model * vec4(aPos, 1.0));
        Normal = mat3(model) * aNormal;   
    
        gl_Position = projection * view * model * vec4(aPos, 1.0f);
    }
"""

@Language("glsl")
val PbrDirectLightFs = """
    #version 300 es
    #define LIGHT_NUMBER ${LightPositions.size}
    precision highp float;
    in vec2 TexCoords;
    in vec3 WorldPos;
    in vec3 Normal;
    
    // material parameters
    uniform vec3 albedo;
    uniform float metallic;
    uniform float roughness;
    uniform float ao;
    uniform vec3 ambient;
    
    // lights
    uniform vec3 lightPositions[LIGHT_NUMBER];
    uniform vec3 lightColors[LIGHT_NUMBER];
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    // ----------------------------------------------------------------------------
    float DistributionGGX(vec3 N, vec3 H, float roughness)
    {
        float a = roughness*roughness;
        float a2 = a*a;
        float NdotH = max(dot(N, H), 0.0);
        float NdotH2 = NdotH*NdotH;
    
        float nom   = a2;
        float denom = (NdotH2 * (a2 - 1.0) + 1.0);
        denom = PI * denom * denom;
    
        return nom / max(denom, 0.001); // prevent divide by zero for roughness=0.0 and NdotH=1.0
    }
    // ----------------------------------------------------------------------------
    float GeometrySchlickGGX(float NdotV, float roughness)
    {
        float r = (roughness + 1.0);
        float k = (r*r) / 8.0;
    
        float nom   = NdotV;
        float denom = NdotV * (1.0 - k) + k;
    
        return nom / denom;
    }
    // ----------------------------------------------------------------------------
    float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
    {
        float NdotV = max(dot(N, V), 0.0);
        float NdotL = max(dot(N, L), 0.0);
        float ggx2 = GeometrySchlickGGX(NdotV, roughness);
        float ggx1 = GeometrySchlickGGX(NdotL, roughness);
    
        return ggx1 * ggx2;
    }
    // ----------------------------------------------------------------------------
    vec3 fresnelSchlick(float cosTheta, vec3 F0)
    {
        return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
    }
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 N = normalize(Normal);
        vec3 V = normalize(camPos - WorldPos);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        // reflectance equation
        vec3 Lo = vec3(0.0);
        for(int i = 0; i < 4; ++i)
        {
            // calculate per-light radiance
            vec3 L = normalize(lightPositions[i] - WorldPos);
            vec3 H = normalize(V + L);
            float distance = length(lightPositions[i] - WorldPos);
            float attenuation = 1.0 / (distance * distance);
            vec3 radiance = lightColors[i] * attenuation;
    
            // Cook-Torrance BRDF
            float NDF = DistributionGGX(N, H, roughness);
            float G   = GeometrySmith(N, V, L, roughness);
            vec3 F    = fresnelSchlick(clamp(dot(H, V), 0.0, 1.0), F0);
    
            vec3 nominator    = NDF * G * F;
            float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0);
            vec3 specular = nominator / max(denominator, 0.001); // prevent divide by zero for NdotV=0.0 or NdotL=0.0
    
            // kS is equal to Fresnel
            vec3 kS = F;
            // for energy conservation, the diffuse and specular light can't
            // be above 1.0 (unless the surface emits light); to preserve this
            // relationship the diffuse component (kD) should equal 1.0 - kS.
            vec3 kD = vec3(1.0) - kS;
            // multiply kD by the inverse metalness such that only non-metals
            // have diffuse lighting, or a linear blend if partly metal (pure metals
            // have no diffuse light).
            kD *= 1.0 - metallic;
    
            // scale light by NdotL
            float NdotL = max(dot(N, L), 0.0);
    
            // add to outgoing radiance Lo
            Lo += (kD * albedo / PI + specular) * radiance * NdotL;  // note that we already multiplied the BRDF by the Fresnel (kS) so we won't multiply by kS again
        }
    
        // ambient lighting (note that the next IBL tutorial will replace
        // this ambient lighting with environment lighting).
        vec3 ambientColor = ambient * albedo * ao;
    
        vec3 color = ambientColor + Lo;
    
        // HDR tonemapping
//        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    } 
"""

@Language("glsl")
val PbrWithIrradianceIBLFs = """
    #version 300 es
    #define LIGHT_NUMBER ${LightPositions.size}
    precision highp float;
    in vec2 TexCoords;
    in vec3 WorldPos;
    in vec3 Normal;
    
    // material parameters
    uniform vec3 albedo;
    uniform float metallic;
    uniform float roughness;
    uniform float ao;
    uniform vec3 ambient;
    
    // IBL
    uniform samplerCube irradianceMap;
    
    // lights
    uniform vec3 lightPositions[LIGHT_NUMBER];
    uniform vec3 lightColors[LIGHT_NUMBER];
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    // ----------------------------------------------------------------------------
    float DistributionGGX(vec3 N, vec3 H, float roughness)
    {
        float a = roughness*roughness;
        float a2 = a*a;
        float NdotH = max(dot(N, H), 0.0);
        float NdotH2 = NdotH*NdotH;
    
        float nom   = a2;
        float denom = (NdotH2 * (a2 - 1.0) + 1.0);
        denom = PI * denom * denom;
    
        return nom / max(denom, 0.001); // prevent divide by zero for roughness=0.0 and NdotH=1.0
    }
    // ----------------------------------------------------------------------------
    float GeometrySchlickGGX(float NdotV, float roughness)
    {
        float r = (roughness + 1.0);
        float k = (r*r) / 8.0;
    
        float nom   = NdotV;
        float denom = NdotV * (1.0 - k) + k;
    
        return nom / denom;
    }
    // ----------------------------------------------------------------------------
    float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
    {
        float NdotV = max(dot(N, V), 0.0);
        float NdotL = max(dot(N, L), 0.0);
        float ggx2 = GeometrySchlickGGX(NdotV, roughness);
        float ggx1 = GeometrySchlickGGX(NdotL, roughness);
    
        return ggx1 * ggx2;
    }
    // ----------------------------------------------------------------------------
    vec3 fresnelSchlick(float cosTheta, vec3 F0)
    {
        return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
    }
    // ----------------------------------------------------------------------------
    vec3 fresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness)
    {
        return F0 + (max(vec3(1.0 - roughness), F0) - F0) * pow(1.0 - cosTheta, 5.0);
    }   
    
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 N = normalize(Normal);
        vec3 V = normalize(camPos - WorldPos);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        // reflectance equation
        vec3 Lo = vec3(0.0);
        for(int i = 0; i < 4; ++i)
        {
            // calculate per-light radiance
            vec3 L = normalize(lightPositions[i] - WorldPos);
            vec3 H = normalize(V + L);
            float distance = length(lightPositions[i] - WorldPos);
            float attenuation = 1.0 / (distance * distance);
            vec3 radiance = lightColors[i] * attenuation;
    
            // Cook-Torrance BRDF
            float NDF = DistributionGGX(N, H, roughness);
            float G   = GeometrySmith(N, V, L, roughness);
            vec3 F    = fresnelSchlick(clamp(dot(H, V), 0.0, 1.0), F0);
    
            vec3 nominator    = NDF * G * F;
            float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0);
            vec3 specular = nominator / max(denominator, 0.001); // prevent divide by zero for NdotV=0.0 or NdotL=0.0
    
            // kS is equal to Fresnel
            vec3 kS = F;
            // for energy conservation, the diffuse and specular light can't
            // be above 1.0 (unless the surface emits light); to preserve this
            // relationship the diffuse component (kD) should equal 1.0 - kS.
            vec3 kD = vec3(1.0) - kS;
            // multiply kD by the inverse metalness such that only non-metals
            // have diffuse lighting, or a linear blend if partly metal (pure metals
            // have no diffuse light).
            kD *= 1.0 - metallic;
    
            // scale light by NdotL
            float NdotL = max(dot(N, L), 0.0);
    
            // add to outgoing radiance Lo
            Lo += (kD * albedo / PI + specular) * radiance * NdotL;  // note that we already multiplied the BRDF by the Fresnel (kS) so we won't multiply by kS again
        }
    
        vec3 kS = fresnelSchlickRoughness(max(dot(N, V), 0.0), F0, roughness);
        
        vec3 kD = 1.0 - kS;
        kD *= 1.0 - metallic;
        
        vec3 irradiance = texture(irradianceMap, N).rgb;
        vec3 diffuse = irradiance * albedo;
        vec3 ambient = (kD * diffuse) * ao;
        
        vec3 color = ambient + Lo;
        
//        color = irradiance;
    
        // HDR tonemapping
        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    } 
"""

@Language("glsl")
const val ProjectionVs = """
    #version 300 es
    in vec3 aPos;
    
    uniform mat4 model;
    uniform mat4 view;
    uniform mat4 projection;
    
    void main()
    {
        gl_Position = projection * view * model * vec4(aPos, 1.0f);
    } 
"""

@Language("glsl")
const val SimpleFs = """
    #version 300 es
    
    out vec4 FragColor;
    
    void main()
    {
        FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    }
"""

@Language("glsl")
const val SimpleVs = """
    #version 300 es
    in vec3 aPos;
    
    void main()
    {
        gl_Position = vec4(aPos.x, aPos.y, aPos.z, 1.0);
    }
"""

@Language("glsl")
const val skyBoxVs = """
    #version 300 es
    in vec3 aPos;
    uniform mat4 projection;
    uniform mat4 view;
    
    out vec3 TexCoords;
    
    void main()
    {
        TexCoords = aPos;
        vec4 pos = projection * view * vec4(aPos, 1.0);
        gl_Position = pos.xyww;
    } 
"""

@Language("glsl")
const val skyBoxFs = """
    #version 300 es
    out vec4 FragColor;

    in vec3 TexCoords;
    
    uniform samplerCube skybox;
    
    void main()
    {    
        FragColor = texture(skybox, TexCoords);
    }
"""

@Language("glsl")
const val envBrdfVs = """
    #version 300 es
    in vec3 aPos;
    in vec2 aTexCoords;
    
    out vec2 TexCoords;
    
    void main()
    {
        TexCoords = aTexCoords;
        gl_Position = vec4(aPos, 1.0);
    }
"""

@Language("glsl")
const val envBrdfFs = """
    #version 300 es
    out vec2 FragColor;
    in vec2 TexCoords;
    
    const float PI = 3.14159265359;
    // ----------------------------------------------------------------------------
    // http://holger.dammertz.org/stuff/notes_HammersleyOnHemisphere.html
    // efficient VanDerCorpus calculation.
    float RadicalInverse_VdC(uint bits)
    {
         bits = (bits << 16u) | (bits >> 16u);
         bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
         bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
         bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
         bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
         return float(bits) * 2.3283064365386963e-10; // / 0x100000000
    }
    // ----------------------------------------------------------------------------
    vec2 Hammersley(uint i, uint N)
    {
        return vec2(float(i)/float(N), RadicalInverse_VdC(i));
    }
    // ----------------------------------------------------------------------------
    vec3 ImportanceSampleGGX(vec2 Xi, vec3 N, float roughness)
    {
        float a = roughness*roughness;
    
        float phi = 2.0 * PI * Xi.x;
        float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a*a - 1.0) * Xi.y));
        float sinTheta = sqrt(1.0 - cosTheta*cosTheta);
    
        // from spherical coordinates to cartesian coordinates - halfway vector
        vec3 H;
        H.x = cos(phi) * sinTheta;
        H.y = sin(phi) * sinTheta;
        H.z = cosTheta;
    
        // from tangent-space H vector to world-space sample vector
        vec3 up          = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
        vec3 tangent   = normalize(cross(up, N));
        vec3 bitangent = cross(N, tangent);
    
        vec3 sampleVec = tangent * H.x + bitangent * H.y + N * H.z;
        return normalize(sampleVec);
    }
    // ----------------------------------------------------------------------------
    float GeometrySchlickGGX(float NdotV, float roughness)
    {
        // note that we use a different k for IBL
        float a = roughness * roughness;
        float k = (a * a) / 2.0;
    
        float nom   = NdotV;
        float denom = NdotV * (1.0 - k) + k;
    
        return nom / denom;
    }
    // ----------------------------------------------------------------------------
    float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
    {
        float NdotV = max(dot(N, V), 0.0);
        float NdotL = max(dot(N, L), 0.0);
        float ggx2 = GeometrySchlickGGX(NdotV, roughness);
        float ggx1 = GeometrySchlickGGX(NdotL, roughness);
    
        return ggx1 * ggx2;
    }
    // ----------------------------------------------------------------------------
    vec2 IntegrateBRDF(float NdotV, float roughness)
    {
        vec3 V;
        V.x = sqrt(1.0 - NdotV*NdotV);
        V.y = 0.0;
        V.z = NdotV;
    
        float A = 0.0;
        float B = 0.0;
    
        vec3 N = vec3(0.0, 0.0, 1.0);
    
        const uint SAMPLE_COUNT = 1024u;
        for(uint i = 0u; i < SAMPLE_COUNT; ++i)
        {
            // generates a sample vector that's biased towards the
            // preferred alignment direction (importance sampling).
            vec2 Xi = Hammersley(i, SAMPLE_COUNT);
            vec3 H = ImportanceSampleGGX(Xi, N, roughness);
            vec3 L = normalize(2.0 * dot(V, H) * H - V);
    
            float NdotL = max(L.z, 0.0);
            float NdotH = max(H.z, 0.0);
            float VdotH = max(dot(V, H), 0.0);
    
            if(NdotL > 0.0)
            {
                float G = GeometrySmith(N, V, L, roughness);
                float G_Vis = (G * VdotH) / (NdotH * NdotV);
                float Fc = pow(1.0 - VdotH, 5.0);
    
                A += (1.0 - Fc) * G_Vis;
                B += Fc * G_Vis;
            }
        }
        A /= float(SAMPLE_COUNT);
        B /= float(SAMPLE_COUNT);
        return vec2(A, B);
    }
    // ----------------------------------------------------------------------------
    void main()
    {
        vec2 integratedBRDF = IntegrateBRDF(TexCoords.x, TexCoords.y);
        FragColor = integratedBRDF;
    }
"""