package rangarok.com.androidpbr.utils

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
    
        gl_Position =  projection * view * vec4(WorldPos, 1.0);
    }
"""

@Language("glsl")
val BRDF = """
    #define MEDIUMP_FLT_MAX    65504.0
    #define MEDIUMP_FLT_MIN    0.00006103515625
    #define saturateMediump(x) min(x, MEDIUMP_FLT_MAX)
    // ----------------------------------------------------------------------------
    float DistributionGGX(vec3 N, vec3 H, float roughness) 
    {
//        float a = roughness*roughness;
//        float a2 = a*a;
//        float NdotH = max(dot(N, H), 0.0);
//        float NdotH2 = NdotH*NdotH;
//
//        float nom   = a2;
//        float denom = (NdotH2 * (a2 - 1.0) + 1.0);
//        denom = PI * denom * denom;
//
//        return saturateMediump(nom / denom);

        // better ndf with spot light shape
        vec3 NxH = cross(N, H);
        float oneMinusNoHSquared = dot(NxH, NxH);
        float NoH = max(dot(N, H), 0.0);
        float a = NoH * roughness;
        float k = roughness / (oneMinusNoHSquared + a * a);
        float d = k * k * (1.0 / PI);
        return saturateMediump(d);
    }
    // ----------------------------------------------------------------------------
    float GeometrySchlickGGX(float NdotV, float roughness)
    {
        float r = (roughness + 1.0);
        float k = (r*r) / 8.0;

        float nom   = NdotV;
        float denom = NdotV * (1.0 - k) + k;

        return saturateMediump(nom / denom);
    }
    // ----------------------------------------------------------------------------
    float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
    {
        float NdotV = max(dot(N, V), 0.0);
        float NdotL = max(dot(N, L), 0.0);
        float ggx2 = GeometrySchlickGGX(NdotV, roughness);
        float ggx1 = GeometrySchlickGGX(NdotL, roughness);

        return saturateMediump(ggx1 * ggx2);
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
      
    vec3 EnvDFGLazarov( vec3 specularColor, float gloss, float ndotv ) {
        //# [ Lazarov 2013, "Getting More Physical in Call of Duty: Black Ops II" ]
        //# Adaptation to fit our G term.
        vec4 p0 = vec4( 0.5745, 1.548, -0.02397, 1.301 );
        vec4 p1 = vec4( 0.5753, -0.2511, -0.02066, 0.4755 );
        vec4 t = gloss * p0 + p1;
        float bias = clamp( t.x * min( t.y, exp2( -7.672 * ndotv ) ) + t.z, 0.0, 1.0);
        float delta = clamp( t.w, 0.0, 1.0);
        float scale = delta - bias;
        bias *= clamp( 50.0 * specularColor.y, 0.0, 1.0);
        return specularColor * scale + bias;
    }  

"""

@Language("glsl")
val LightingVarDeclartion = """
    #define POINT_LIGHT_NUMBER ${PointLightPositions.size}
     // lights
    uniform vec3 pointLightPositions[POINT_LIGHT_NUMBER];
    uniform vec3 pointLightColors[POINT_LIGHT_NUMBER];
    
    uniform vec3 directionLightDir;
    uniform vec3 directionLightColor;
"""

@Language("glsl")
val LightingCalculation = """
    // reflectance equation
    vec3 Lo = vec3(0.0);
    // point light
    for(int i = 0; i < POINT_LIGHT_NUMBER; ++i) {
        // calculate per-light radiance
        vec3 L = normalize(pointLightPositions[i] - WorldPos);
        vec3 H = normalize(V + L);
        float distance = length(pointLightPositions[i] - WorldPos);
        float attenuation = 1.0 / (distance * distance);
        vec3 radiance = pointLightColors[i] * attenuation;
    
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
    // directional light
    {
        vec3 L = normalize(-directionLightDir);
        vec3 H = normalize(V + L);
        vec3 radiance = directionLightColor;

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
        Lo += (kD * albedo + specular) * radiance * NdotL;  // note that we already multiplied the BRDF by the Fresnel (kS) so we won't multiply by kS again
    }    
"""

@Language("glsl")
val PbrDirectLightFs = """
    #version 300 es
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
    
    $LightingVarDeclartion
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    
    $BRDF
    
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 N = normalize(Normal);
        vec3 V = normalize(camPos - WorldPos);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        $LightingCalculation
    
        // ambient lighting (note that the next IBL tutorial will replace
        // this ambient lighting with environment lighting).
        vec3 ambientColor = ambient * albedo * ao;
    
        vec3 color = ambientColor + Lo;
    
        // HDR tonemapping
        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    } 
"""

@Language("glsl")
val PbrWithIrradianceIBLFs = """
    #version 300 es
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
    $LightingVarDeclartion
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    
    $BRDF
    
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
        $LightingCalculation
    
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
val PbrWithSpecularRadianceIBLFs = """
    #version 300 es
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
    uniform samplerCube radianceMap;
    uniform sampler2D envBrdfMap;
    
    $LightingVarDeclartion
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    $BRDF   
    
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 N = Normal;
        vec3 V = normalize(camPos - WorldPos);
        vec3 R = reflect(-V, N);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        // reflectance equation
        vec3 Lo = vec3(0.0);
        for(int i = 0; i < LIGHT_NUMBER; ++i)
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
    
        vec3 F = fresnelSchlickRoughness(max(dot(N, V), 0.0), F0, roughness);
        vec3 kS = F;
        
        vec3 kD = 1.0 - kS;
        kD *= 1.0 - metallic;
        
        vec3 irradiance = texture(irradianceMap, N).rgb;
        vec3 diffuse = irradiance * albedo;
        
        const float MAX_RADIANCE_LOD = 6.0;
        vec3 radiance = textureLod(radianceMap, R, roughness * MAX_RADIANCE_LOD).rgb;
        vec2 brdf = texture(envBrdfMap, vec2(max(dot(N, V), 0.0), roughness)).rg;
        vec3 specular = radiance * (F * brdf.x + brdf.y);
        
        vec3 ambient = (kD * diffuse + specular) * ao;
        
        vec3 color = ambient + Lo;
        
//        color = vec3(brdf, 1.0);
    
        // HDR tonemapping
        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    }
"""

@Language("glsl")
val PbrWithSpecularRadianceIBLFAndEnvBrdCalcs = """
    #version 300 es
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
    uniform samplerCube radianceMap;
    uniform sampler2D envBrdfMap;
    
    $LightingVarDeclartion
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    
    $BRDF
    
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 N = Normal;
        vec3 V = normalize(camPos - WorldPos);
        vec3 R = reflect(-V, N);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        $LightingCalculation
    
        vec3 F = fresnelSchlickRoughness(max(dot(N, V), 0.0), F0, roughness);
        vec3 kS = F;
        
        vec3 kD = 1.0 - kS;
        kD *= 1.0 - metallic;
        
        vec3 irradiance = texture(irradianceMap, N).rgb;
        vec3 diffuse = irradiance * albedo;
        
        const float MAX_RADIANCE_LOD = ${RadianceMipmapLevel - 1}.0;
        vec3 radiance = textureLod(radianceMap, R, roughness * MAX_RADIANCE_LOD).rgb;
        vec3 envBrdf = EnvDFGLazarov(F0, metallic, max(dot(N, V), 0.0));
        vec3 specular = radiance * (F * envBrdf);
        
        vec3 ambient = (kD * diffuse + specular) * ao;
        
        vec3 color = ambient + Lo;
        
//        color = vec3(brdf, 1.0);
    
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
        vec3 color = texture(skybox, TexCoords).rgb;
        // HDR tonemapping
        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    }
"""

@Language("glsl")
const val EnvBrdfVs = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoords;
    
    out vec2 TexCoords;
    
    void main()
    {
        TexCoords = aTexCoords;
        gl_Position = vec4(aPos, 1.0);
    }
"""

@Language("glsl")
const val EnvBrdfFs = """#version 300 es
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
float VanDerCorpus(uint n, uint base)
{
    float invBase = 1.0 / float(base);
    float denom   = 1.0;
    float result  = 0.0;

    for(uint i = 0u; i < 32u; ++i)
    {
        if(n > 0u)
        {
            denom   = mod(float(n), 2.0);
            result += denom * invBase;
            invBase = invBase / 2.0;
            n       = uint(float(n) / 2.0);
        }
    }

    return result;
}
// ----------------------------------------------------------------------------
vec2 HammersleyNoBitOps(uint i, uint N)
{
    return vec2(float(i)/float(N), VanDerCorpus(i, 2u));
}
// ----------------------------------------------------------------------------
vec3 ImportanceSampleGGX(vec2 Xi, vec3 N, float roughness)
{
    float a = roughness;

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
        vec2 Xi = HammersleyNoBitOps(i, SAMPLE_COUNT);
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
}"""

@Language("glsl")
val PbrWithSpecularRadianceIBLFAndEnvBrdCalcsAndTextures = """
    #version 300 es
    #define POINT_LIGHT_NUMBER ${PointLightPositions.size}
    precision highp float;
    in vec2 TexCoords;
    in vec3 WorldPos;
    in vec3 Normal;
    
    // material parameters
    uniform sampler2D albedoMap;
    uniform sampler2D normalMap;
    uniform sampler2D metallicMap;
    uniform sampler2D roughnessMap;
    uniform sampler2D aoMap;
    
    // IBL
    uniform samplerCube irradianceMap;
    uniform samplerCube radianceMap;
    
    $LightingVarDeclartion
    
    uniform vec3 camPos;
    
    out vec4 FragColor;
    
    const float PI = 3.14159265359;
    
    vec3 getNormalFromMap()
    {
        vec3 tangentNormal = texture(normalMap, TexCoords).xyz * 2.0 - 1.0;
    
        vec3 Q1  = dFdx(WorldPos);
        vec3 Q2  = dFdy(WorldPos);
        vec2 st1 = dFdx(TexCoords);
        vec2 st2 = dFdy(TexCoords);
    
        vec3 N   = normalize(Normal);
        vec3 T  = normalize(Q1*st2.t - Q2*st1.t);
        vec3 B  = -normalize(cross(N, T));
        mat3 TBN = mat3(T, B, N);
    
        return normalize(TBN * tangentNormal);
    }
    
    $BRDF
    
    // ----------------------------------------------------------------------------
    void main()
    {
        vec3 albedo = pow(texture(albedoMap, TexCoords).rgb, vec3(2.2));
        float metallic = texture(metallicMap, TexCoords).r;
        float roughness = texture(roughnessMap, TexCoords).r;
        float ao = texture(aoMap, TexCoords).r;
        
        vec3 N = getNormalFromMap();
        vec3 V = normalize(camPos - WorldPos);
        vec3 R = reflect(-V, N);
    
        // calculate reflectance at normal incidence; if dia-electric (like plastic) use F0
        // of 0.04 and if it's a metal, use the albedo color as F0 (metallic workflow)
        vec3 F0 = vec3(0.04);
        F0 = mix(F0, albedo, metallic);
    
        $LightingCalculation
    
        vec3 F = fresnelSchlickRoughness(max(dot(N, V), 0.0), F0, roughness);
        vec3 kS = F;
        
        vec3 kD = 1.0 - kS;
        kD *= 1.0 - metallic;
        
        vec3 irradiance = texture(irradianceMap, N).rgb;
        vec3 diffuse = irradiance * albedo;
        
        const float MAX_RADIANCE_LOD = $RadianceMipmapLevel.0;
        vec3 radiance = textureLod(radianceMap, R, roughness * MAX_RADIANCE_LOD).rgb;
        vec3 envBrdf = EnvDFGLazarov(F0, roughness, max(dot(N, V), 0.0));
        vec3 specular = radiance * (F * envBrdf);

//        vec2 envBrdf = EnvBRDFLUTApprox(F0, roughness, max(dot(N, V), 0.0));
//        vec3 specular = radiance * (F * envBrdf.x + envBrdf.y);
        
        vec3 ambient = (kD * diffuse + specular) * ao;
        
        vec3 color = ambient + Lo;
    
        // HDR tonemapping
        color = color / (color + vec3(1.0));
        // gamma correct
        color = pow(color, vec3(1.0/2.2));
    
        FragColor = vec4(color, 1.0);
    }
"""

@Language("glsl")
const val CubeMapVs = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    
    out vec3 WorldPos;
    
    uniform mat4 projection;
    uniform mat4 view;
    
    void main()
    {
        WorldPos = aPos;  
        gl_Position =  projection * view * vec4(WorldPos, 1.0);
    }
"""

@Language("glsl")
const val CubeMapConversionFs = """
    #version 300 es
    out vec4 FragColor;
    in vec3 WorldPos;
    
    uniform sampler2D equirectangularMap;
    
    const vec2 invAtan = vec2(0.1591, 0.3183);
    vec2 SampleSphericalMap(vec3 v)
    {
        vec2 uv = vec2(atan(v.z, v.x), -asin(v.y));
        uv *= invAtan;
        uv += 0.5;
        return uv;
    }
    
    void main()
    {		
        vec2 uv = SampleSphericalMap(normalize(WorldPos));
        vec3 color = texture(equirectangularMap, uv).rgb;
        
        FragColor = vec4(color, 1.0);
    }

"""

@Language("glsl")
const val IrrandianceCalcFs = """
    #version 300 es
    out vec4 FragColor;
    in vec3 WorldPos;
    
    uniform samplerCube environmentMap;
    
    const float PI = 3.14159265359;
    
    void main()
    {		
        vec3 N = normalize(WorldPos);
    
        vec3 irradiance = vec3(0.0);   
        
        // tangent space calculation from origin point
        vec3 up    = vec3(0.0, 1.0, 0.0);
        vec3 right = cross(up, N);
        up            = cross(N, right);
           
        float sampleDelta = 0.025;
        float nrSamples = 0.0f;
        for(float phi = 0.0; phi < 2.0 * PI; phi += sampleDelta)
        {
            for(float theta = 0.0; theta < 0.5 * PI; theta += sampleDelta)
            {
                // spherical to cartesian (in tangent space)
                vec3 tangentSample = vec3(sin(theta) * cos(phi),  sin(theta) * sin(phi), cos(theta));
                // tangent space to world
                vec3 sampleVec = tangentSample.x * right + tangentSample.y * up + tangentSample.z * N; 
    
                irradiance += texture(environmentMap, sampleVec).rgb * cos(theta) * sin(theta);
                nrSamples++;
            }
        }
        irradiance = PI * irradiance * (1.0 / float(nrSamples));
        
        FragColor = vec4(irradiance, 1.0);
    }

"""

@Language("glsl")
const val RadianceCalcFs = """
   #version 300 es
    out vec4 FragColor;
    in vec3 WorldPos;
    
    uniform samplerCube environmentMap;
    uniform float roughness;
    
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
    
        return nom / denom;
    }
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
    void main()
    {		
        vec3 N = normalize(WorldPos);
        
        // make the simplyfying assumption that V equals R equals the normal 
        vec3 R = N;
        vec3 V = R;
    
        const uint SAMPLE_COUNT = 1024u;
        vec3 prefilteredColor = vec3(0.0);
        float totalWeight = 0.0;
        
        for(uint i = 0u; i < SAMPLE_COUNT; ++i)
        {
            // generates a sample vector that's biased towards the preferred alignment direction (importance sampling).
            vec2 Xi = Hammersley(i, SAMPLE_COUNT);
            vec3 H = ImportanceSampleGGX(Xi, N, roughness);
            vec3 L  = normalize(2.0 * dot(V, H) * H - V);
    
            float NdotL = max(dot(N, L), 0.0);
            if(NdotL > 0.0)
            {
                // sample from the environment's mip level based on roughness/pdf
                float D   = DistributionGGX(N, H, roughness);
                float NdotH = max(dot(N, H), 0.0);
                float HdotV = max(dot(H, V), 0.0);
                float pdf = D * NdotH / (4.0 * HdotV) + 0.0001; 
    
                float resolution = 512.0; // resolution of source cubemap (per face)
                float saTexel  = 4.0 * PI / (6.0 * resolution * resolution);
                float saSample = 1.0 / (float(SAMPLE_COUNT) * pdf + 0.0001);
    
                float mipLevel = roughness == 0.0 ? 0.0 : 0.5 * log2(saSample / saTexel); 
                
                prefilteredColor += texture(environmentMap, L, mipLevel).rgb * NdotL;
                totalWeight      += NdotL;
            }
        }
    
        prefilteredColor = prefilteredColor / totalWeight;
    
        FragColor = vec4(prefilteredColor, 1.0);
    }
 
"""