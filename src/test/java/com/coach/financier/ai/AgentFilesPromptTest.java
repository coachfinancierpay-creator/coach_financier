package com.coach.financier.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composition du prompt système : gabarit {@code generic.txt} + agent principal + agent spécialisé.
 * Vérifie aussi que les marqueurs de zone éditable (atelier d'optimisation des prompts) ne sont
 * JAMAIS envoyés au LLM — le prompt de production doit rester identique à l'existant.
 */
class AgentFilesPromptTest {

    @Test
    void compositionIncludesTemplatePrincipalAndSpecializedPrompt() {
        String prompt = AgentFiles.composeSystemPrompt("GABARIT\n[agent_principal]\n[agent]\nFIN",
                "PRINCIPAL", "SPECIALISE");

        assertTrue(prompt.contains("GABARIT"));
        assertTrue(prompt.contains("PRINCIPAL"));
        assertTrue(prompt.contains("SPECIALISE"));
        assertTrue(prompt.contains("FIN"));
        assertTrue(prompt.indexOf("PRINCIPAL") < prompt.indexOf("SPECIALISE"),
                "l'agent principal précède l'agent spécialisé");
    }

    @Test
    void zoneMarkersAreNeverSentToTheLlm() {
        String prompt = AgentFiles.composeSystemPrompt("GABARIT\n[agent_principal]\n[agent]",
                "[[[\nPRINCIPAL\n]]]", "[[[\nSPECIALISE\n]]]");

        assertFalse(prompt.contains("[[["), "les marqueurs de zone ne doivent jamais être envoyés au LLM");
        assertFalse(prompt.contains("]]]"), "les marqueurs de zone ne doivent jamais être envoyés au LLM");
        assertTrue(prompt.contains("PRINCIPAL"));
        assertTrue(prompt.contains("SPECIALISE"));
    }

    @Test
    void stripZoneMarkersKeepsTheRestIntact() {
        String stripped = AgentFiles.stripZoneMarkers("ligne 1\n[[[\nligne 2\n]]]\nligne 3\n");

        assertTrue(stripped.contains("ligne 1"));
        assertTrue(stripped.contains("ligne 2"));
        assertTrue(stripped.contains("ligne 3"));
        assertFalse(stripped.contains("[[["));
        assertFalse(stripped.contains("]]]"));
    }

    @Test
    void stripZoneMarkersLeavesAnUnmarkedPromptUnchanged() {
        String prompt = "Tu es un coach financier.\nRéponds en français.";

        assertTrue(prompt.equals(AgentFiles.stripZoneMarkers(prompt)));
    }

    /** Agent générique : le gabarit + l'agent principal, sans agent spécialisé (pas d'inclusion récursive). */
    @Test
    void genericAgentReceivesPrincipalWithoutSpecializedPrompt() {
        String prompt = AgentFiles.systemPromptFor(AgentFiles.GENERIC_THEME);

        assertFalse(prompt.contains("[agent_principal]"));
        assertFalse(prompt.contains("[agent]"));
        assertFalse(prompt.contains("[[["));
        assertFalse(prompt.contains("]]]"));
        assertTrue(prompt.contains("CONVERSATION CONTINUITY"), "l'agent principal est inclus");
        assertFalse(prompt.contains("Consumer Loan"), "aucun agent spécialisé n'est inclus");
    }

    /** Agent spécialisé : gabarit + agent principal + agent du thème, sans marqueur visible. */
    @Test
    void specializedAgentReceivesItsOwnPromptAndNoMarker() {
        String prompt = AgentFiles.systemPromptFor("credit_conso");

        assertFalse(prompt.contains("[[["));
        assertFalse(prompt.contains("]]]"));
        assertTrue(prompt.contains("CONVERSATION CONTINUITY"), "l'agent principal est inclus");
        assertTrue(prompt.contains("Consumer Loan"), "l'agent spécialisé est inclus");
    }

    /** Les deux agents de l'atelier sont chargés depuis ./agent (et non depuis le repli de sécurité). */
    @Test
    void promptLabAgentsUseTheirRealPromptFiles() {
        assertTrue(AgentFiles.promptControllerSystemPrompt().contains("STABILITY"),
                "le vrai prompt du contrôleur est chargé (pas le repli)");
        assertTrue(AgentFiles.promptEditorSystemPrompt().contains("ANTI-OVERFITTING"),
                "le vrai prompt de l'éditeur est chargé (pas le repli)");
    }

    /** L'atelier n'est pas lui-même optimisable : aucun de ses prompts ne porte de marqueur. */
    @Test
    void promptLabAgentsCarryNoEditableZone() {
        assertFalse(AgentFiles.promptControllerSystemPrompt().contains("[[["));
        assertFalse(AgentFiles.promptEditorSystemPrompt().contains("[[["));
    }

    /**
     * CONTRAT DU CLIENT SIMULÉ (« Agent C ») : la DERNIÈRE question autorisée (`turnNumber == depth`) doit être
     * POSÉE, et non remplacée par une phrase de clôture — sinon le scénario s'arrête une question trop tôt.
     * <p>
     * Bogue constaté en réel par l'utilisateur : « quand je choisis 3, le client a posé 2 questions ; 5 → 4 ».
     * Le prompt demandait au client de conclure (`endConversation` à `true`) dès que `turnNumber` atteignait
     * `depth` : sa dernière question était donc consommée par une clôture, et l'IHM fermait le scénario.
     */
    @Test
    void theSimulatedClientAsksItsLastAllowedQuestion() {
        String prompt = AgentFiles.promptClientSystemPrompt();

        assertTrue(prompt.contains("Question number `depth` is your LAST AUTHORISED question"),
                "la dernière question autorisée doit être posée : " + prompt);
        assertTrue(prompt.contains("NEVER anticipate the closure because of the counter"),
                "aucune clôture ne doit être provoquée par le compteur de profondeur");
        assertFalse(prompt.contains("you conclude the scenario (`endConversation` set to `true`) instead of asking"),
                "la règle qui faisait perdre la dernière question ne doit pas revenir");
    }

    /**
     * CONTRAT DU RAPPEL CONSEILLER : à côté du lien de rendez-vous, le Coach doit proposer un jeton SANS URL
     * (`[RAPPEL|Être rappelé par un conseiller]`) — c'est lui qui ouvre la pop-in « un conseiller vous
     * recontactera ». Le format exact est verrouillé ici : l'IHM ne reconnaît que celui-là.
     */
    @Test
    void theCallbackTokenIsDocumentedInThePrincipalPrompt() {
        String prompt = AgentFiles.systemPromptFor(AgentFiles.GENERIC_THEME);

        assertTrue(prompt.contains("[RAPPEL|Get a callback from an advisor]"),
                "le jeton de rappel fait partie du format des liens : " + prompt);
        assertTrue(prompt.contains("[URL|Book an appointment with an advisor|"),
                "le lien de rendez-vous reste proposé en même temps que le rappel");
        assertTrue(prompt.contains("as soon as possible"),
                "le prompt décrit le message de la pop-in (recontacter dans les plus brefs délais)");
        assertTrue(prompt.contains("WITHOUT a URL"),
                "le jeton ne doit jamais être transformé en lien ni accompagné d'une adresse inventée");
    }

    /**
     * L'agent crédit conso reçoit bien le jeton de rappel (règle transverse de l'agent principal, composée
     * dans le prompt de chaque agent).
     * <p>
     * ⚠️ La règle « lien de SOUSCRIPTION » (`url_souscription`) n'est plus portée par `agent/credit-conso.txt`
     * dans sa version réécrite en sections numérotées : elle n'est donc pas vérifiée ici (voir
     * {@link #theSimulationRuleListsTheMandatoryFigures}). Le champ reste déclaré et whitelisté côté
     * back-office (`ProductUrlIndexTest`), pour qu'une URL de souscription citée par le Coach ne soit jamais
     * prise pour une URL inventée.
     */
    @Test
    void theConsumerCreditAgentOffersTheCallbackTokenToo() {
        String prompt = AgentFiles.systemPromptFor("credit_conso");

        assertTrue(prompt.contains("[RAPPEL|Get a callback from an advisor]"),
                "l'agent crédit conso propose aussi le rappel conseiller");
        assertTrue(prompt.contains("WITHOUT a URL"),
                "le jeton de rappel n'est jamais transformé en lien (aucune adresse de rappel n'existe)");
    }

    /**
     * CONTRAT DE LA SIMULATION (agent crédit conso) : quand le client demande un chiffrage, le Coach doit
     * présenter le chiffrage sous forme de TABLEAU, avec les colonnes attendues, les données réellement
     * disponibles (taux débiteur, frais de dossier) et « non renseigné » plutôt qu'une valeur inventée.
     * <p>
     * ⚠️ La règle « lien de SOUSCRIPTION » (`url_souscription`) n'est plus portée par `agent/credit-conso.txt`
     * depuis sa réécriture en sections numérotées : elle n'est donc plus vérifiée ici. Le champ reste déclaré
     * et whitelisté côté back-office (`ProductUrlIndexTest`), pour qu'une URL de souscription citée par le
     * Coach ne soit jamais prise pour une URL inventée.
     */
    @Test
    void theSimulationRuleListsTheMandatoryFigures() {
        String prompt = AgentFiles.systemPromptFor("credit_conso");
        // Les règles sont mises en forme (retours à la ligne au fil des 110 colonnes) : on compare donc sur une
        // copie dont les espaces sont normalisés, sinon une phrase coupée entre deux lignes échapperait au test.
        String flat = prompt.replaceAll("\\s+", " ");

        assertTrue(flat.contains("loan amount, term, monthly payment, fixed APR, total cost of the loan, "
                        + "total amount due"),
                "les colonnes d'un chiffrage doivent être listées, dans cet ordre : " + prompt);
        for (String figure : new String[]{"fixed annual borrowing rate", "arrangement fees",
                "total cost of the loan", "total amount due"}) {
            assertTrue(flat.contains(figure), "information obligatoire absente : " + figure);
        }
        assertTrue(flat.contains("TABLE"),
                "la simulation est restituée sous forme de tableau (une ligne par durée)");
        assertTrue(flat.contains("not documented"),
                "une donnée absente se déclare « non renseigné » : jamais une case vide ni une valeur inventée");
        assertTrue(flat.contains("Do not add any additional column"),
                "le tableau reste borné aux colonnes utiles (lisibilité dans la bulle de chat)");
    }

    /**
     * CONTRAT DU VOCABULAIRE INTERNE : le Coach s'appuie sur ses documents (arbre de décision, catalogue,
     * grille de taux) mais ne les NOMME jamais au client — « d'après l'arbre de décision » ne veut rien dire
     * pour lui. Règle transverse (agent principal) + rappel dans l'agent crédit conso.
     */
    @Test
    void theInternalVocabularyIsNeverShownToTheClient() {
        String principal = AgentFiles.systemPromptFor(AgentFiles.GENERIC_THEME);

        assertTrue(principal.contains("Internal vocabulary"),
                "la règle est présente dans l'agent principal (chargé par tous les agents)");
        assertTrue(principal.contains("based on the decision tree"),
                "le terme interdit est cité en exemple de ce qu'il ne faut pas écrire");
        assertTrue(principal.contains("based on your project and your situation"),
                "une formulation de remplacement est fournie au Coach");
        assertTrue(AgentFiles.systemPromptFor("credit_conso").contains("\"decision tree\""),
                "l'agent crédit conso rappelle de ne pas nommer l'arbre de décision");
    }

    /** Les acronymes bancaires sont expliqués à la première utilisation, sauf si le client les a déjà employés. */
    @Test
    void bankingAcronymsAreExplainedUnlessTheClientUsedThemFirst() {
        String principal = AgentFiles.systemPromptFor(AgentFiles.GENERIC_THEME);

        assertTrue(principal.contains("Banking acronyms — explain the first occurrence"),
                "la règle de pédagogie des acronymes est présente dans l'agent principal");
        assertTrue(principal.contains("first write its full name, then the acronym in brackets"),
                "un acronyme introduit par le Coach doit être développé");
        assertTrue(principal.contains("If the customer has used this acronym or abbreviation before you"),
                "l'exception pour un acronyme déjà utilisé par le client est explicitement documentée");
        assertTrue(AgentFiles.systemPromptFor("credit_conso").contains("APR, PEA, PER, RIB or IBAN"),
                "la règle transverse est composée dans le prompt de l'agent crédit conso");
    }

    /**
     * CONTRAT DE TON HUMAIN : le Coach ne réutilise pas la même accroche d'un tour à l'autre (ce qui fait
     * « robot ») et évite les tournures de rapport. Constaté en réel : « Bonne nouvelle : sur la base des
     * données dont je dispose, votre projet est réalisable. » revenait à chaque réponse.
     */
    @Test
    void theCoachMustSoundHumanAndVaryItsOpening() {
        String principal = AgentFiles.systemPromptFor(AgentFiles.GENERIC_THEME);

        assertTrue(principal.contains("Never reuse the same opening sentence"),
                "la règle anti-répétition de l'accroche est présente");
        assertTrue(principal.contains("\"Good news: ...\""),
                "l'accroche constatée en réel est citée comme formule à ne pas répéter");
        assertTrue(principal.contains("Speak like a person, not like a robot"),
                "la section de ton humain est présente");
        assertTrue(principal.contains("never the same sentence copied word for word"),
                "les mentions obligatoires restent dues, mais reformulées");
        assertTrue(principal.contains("does not repeat the one from my previous answer"),
                "la checklist finale fait vérifier l'accroche");
        assertTrue(AgentFiles.systemPromptFor("credit_conso").contains("Never reuse the same opening sentence"),
                "la règle transverse est bien composée dans le prompt de l'agent crédit conso");
    }
}
