/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.mss.examples.petstore.pet;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.metrics.annotation.Timed;
import org.wso2.carbon.mss.examples.petstore.util.JedisUtil;
import org.wso2.carbon.mss.examples.petstore.util.model.Pet;
import org.wso2.carbon.mss.httpmonitoring.HTTPMonitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Pet microservice.
 */
@HTTPMonitoring
@Path("/pet")
public class PetService {
    private static final Logger log = LoggerFactory.getLogger(PetService.class);

    static {
        log.info("SENTINEL_HOST: {}", JedisUtil.getSentinelHost());
        log.info("SENTINEL1_PORT: {}", JedisUtil.getSentinelPort());
    }

    @POST
    @Consumes("application/json")
    @Timed
    public Response addPet(Pet pet) {
        String categoryName = pet.getCategory().getName();
        if (!JedisUtil.smembers(PetConstants.CATEGORIES_KEY).contains(categoryName)) {
            JedisUtil.sadd(PetConstants.CATEGORIES_KEY, categoryName);
        }
        String categoryKey = PetConstants.CATEGORY_KEY_PREFIX + categoryName;
        JedisUtil.sadd(categoryKey, pet.getId());
        String id = pet.getId();
        String petKey = PetConstants.PET_ID_KEY_PREFIX + id;
        if (JedisUtil.get(petKey) != null) {
            return Response.status(Response.Status.CONFLICT).
                    entity("Pet with ID " + id + " already exists").build();
        } else {
            JedisUtil.set(petKey, new Gson().toJson(pet));
            log.info("Added pet");
        }
        return Response.status(Response.Status.OK).entity("Pet with ID " + id + " successfully added").build();
    }

    @DELETE
    @Path("/{id}")
    @Timed
    public Response deletePet(@PathParam("id") String id) {
        String petKey = PetConstants.PET_ID_KEY_PREFIX + id;
        String petValue = JedisUtil.get(petKey);
        if (petValue == null || petValue.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Pet pet = new Gson().fromJson(petValue, Pet.class);
        String categoryKey = PetConstants.CATEGORY_KEY_PREFIX + pet.getCategory().getName();
        JedisUtil.srem(categoryKey, pet.getId());
        JedisUtil.del(petKey);
        log.info("Deleted pet");
        return Response.status(Response.Status.OK).entity("OK").build();
    }

    @PUT
    @Consumes("application/json")
    @Timed
    public Response updatePet(Pet pet) {
        String id = pet.getId();
        String petKey = PetConstants.PET_ID_KEY_PREFIX + id;
        String json = JedisUtil.get(petKey);
        if (json == null || json.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            JedisUtil.set(petKey, new Gson().toJson(pet));
            log.info("Updated pet");
            return Response.status(Response.Status.OK).entity("Pet with ID " + id + " successfully updated").build();
        }
    }

    @GET
    @Produces("application/json")
    @Path("/{id}")
    @Timed
    public Response getPet(@PathParam("id") String id) {
        String json = JedisUtil.get(PetConstants.PET_ID_KEY_PREFIX + id);
        if (json == null || json.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        log.info("Got pet");
        return Response.status(Response.Status.OK).entity(new Gson().fromJson(json, Pet.class)).build();
    }

    @GET
    @Path("/all")
    @Produces("application/json")
    @Timed
    public List<Pet> getAllPets() {
        List<Pet> result = new ArrayList<>();
        Set<String> categories = JedisUtil.smembers(PetConstants.CATEGORIES_KEY);
        for (String category : categories) {
            Set<String> pets = JedisUtil.smembers(PetConstants.CATEGORY_KEY_PREFIX + category);
            for (String petID : pets) {
                String petValue = JedisUtil.get(PetConstants.PET_ID_KEY_PREFIX + petID);
                result.add(new Gson().fromJson(petValue, Pet.class));
            }
        }
        return result;
    }
}
