/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.webserver.api.webauthn;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.exception.InvalidTokenException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class GetUserFromRecoverAccountTokenAPI extends WebserverAPI {
    public GetUserFromRecoverAccountTokenAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/user/recover";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        String token = InputParser.getQueryParamOrThrowError(req, "token", false);

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            AuthRecipeUserInfo user = WebAuthN.getUserForToken(storage, tenantIdentifier, token);

            io.supertokens.useridmapping.UserIdMapping.populateExternalUserIdForUsers(
                    tenantIdentifier.toAppIdentifier(), storage, new AuthRecipeUserInfo[]{user});

            String recipeUserId = null;

            for (LoginMethod lm : user.loginMethods) {
                if (lm.recipeId.equals(RECIPE_ID.WEBAUTHN)) {
                    if (lm.tenantIds.contains(tenantIdentifier.getTenantId())) {
                        recipeUserId = lm.getSupertokensOrExternalUserId();
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("recipeUserId", recipeUserId);
            response.add("user", user.toJson(getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_3)));

            sendJsonResponse(200, response, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | NoSuchAlgorithmException e) {
            throw new ServletException(e);
        } catch (InvalidTokenException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "RECOVER_ACCOUNT_TOKEN_INVALID_ERROR");
            sendJsonResponse(200, response, resp);
        }
    }
}
