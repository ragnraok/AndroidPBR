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
        color = color / (color + vec3(1.0));
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
val PbrWithSpecularRadianceIBLFs = """
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
    uniform samplerCube radianceMap;
    uniform sampler2D envBrdfMap;
    
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

        return nom / denom;
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
    uniform samplerCube radianceMap;
    uniform sampler2D envBrdfMap;
    
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

        return nom / denom;
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
      
    vec3 EnvBrdfCalcFunc( vec3 specularColor, float gloss, float ndotv ) {
        vec4 p0 = vec4( 0.5745, 1.548, -0.02397, 1.301 );
        vec4 p1 = vec4( 0.5753, -0.2511, -0.02066, 0.4755 );
        vec4 t = gloss * p0 + p1;
        float bias = clamp( t.x * min( t.y, exp2( -7.672 * ndotv ) ) + t.z, 0.0, 1.0);
        float delta = clamp( t.w, 0.0, 1.0);
        float scale = delta - bias;
        bias *= clamp( 50.0 * specularColor.y, 0.0, 1.0);
        return specularColor * scale + bias;
    }  
    
    vec2 EnvBRDFLUTApprox(float roughness, float NdotV) {
        //# [ Lazarov 2013, "Getting More Physical in Call of Duty: Black Ops II" ]
        //# Adaptation to fit our G term.
        vec4 c0 = vec4(-1, -0.0275, -0.572, 0.022);
        vec4 c1 = vec4(1, 0.0425, 1.04, -0.04);
        vec4 r = c0 * (roughness) + c1;
        float a004 = min(r.x * r.x, pow(-9.28 * NdotV, 2.0)) * r.x + r.y;
        vec2 AB = vec2(-1.04, 1.04)*(a004) + vec2(r.z, r.w);
        return AB;
    }
    
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
        vec3 envBrdf = EnvBrdfCalcFunc(F0, metallic, max(dot(N, V), 0.0));
        vec3 specular = radiance * (F * envBrdf);

//        vec2 envBrdf = EnvBRDFLUTApprox(roughness, max(dot(N, V), 0.0));
//        vec3 specular = radiance * (F * envBrdf.x + envBrdf.y);
        
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
        FragColor = texture(skybox, TexCoords);
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
    #define LIGHT_NUMBER ${LightPositions.size}
    
    #define MEDIUMP_FLT_MAX    65504.0
    #define MEDIUMP_FLT_MIN    0.00006103515625
    #define saturateMediump(x) min(x, MEDIUMP_FLT_MAX)
    
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
    
    // lights
    uniform vec3 lightPositions[LIGHT_NUMBER];
    uniform vec3 lightColors[LIGHT_NUMBER];
    
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
        vec4 p0 = vec4( 0.5745, 1.548, -0.02397, 1.301 );
        vec4 p1 = vec4( 0.5753, -0.2511, -0.02066, 0.4755 );
        vec4 t = gloss * p0 + p1;
        float bias = clamp( t.x * min( t.y, exp2( -7.672 * ndotv ) ) + t.z, 0.0, 1.0);
        float delta = clamp( t.w, 0.0, 1.0);
        float scale = delta - bias;
        bias *= clamp( 50.0 * specularColor.y, 0.0, 1.0);
        return specularColor * scale + bias;
    }  
    
    vec2 EnvBRDFLUTApprox(vec3 SpecularColor, float roughness, float NdotV) {
        //# [ Lazarov 2013, "Getting More Physical in Call of Duty: Black Ops II" ]
        //# Adaptation to fit our G term.
        const vec4 c0 = vec4( -1.0, -0.0275, -0.572, 0.022 );
        const vec4 c1 = vec4( 1.0, 0.0425, 1.04, -0.04 );
        vec4 r = roughness * c0 + c1;
        float a004 = min( r.x * r.x, exp2( -9.28 * NdotV ) ) * r.x + r.y;
        vec2 AB = vec2( -1.04, 1.04 ) * a004 + r.zw;
        AB.y *= clamp( 50.0 * SpecularColor.y , 0.0, 1.0);
        return vec2(SpecularColor * AB.x + AB.y);
    }
    
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