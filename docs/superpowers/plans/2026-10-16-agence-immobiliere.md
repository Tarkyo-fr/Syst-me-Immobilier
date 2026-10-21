# Agence immobilière Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tout achat, location, prolongation et revente de bien passe par une agence (catalogue + fiche + « Mes biens »), la porte ne portant plus que la configuration du bien.

**Architecture:** La logique métier (catalogue, achat, location, prolongation, revente, limites, entreprises, images) vit dans `minenorth-portes` (nouveau paquet `fr.minenorth.portes.agency`), car les données des portes y sont. Un nouveau mod `minenorth-immo` ne contient que les écrans et les paquets réseau de l'agence et appelle `AgencyService`. Seule la logique pure (`ImmoRules`) a des tests automatiques : le reste (Minecraft) se vérifie à la main sur un serveur de test.

**Tech Stack:** Forge 1.20.1 (47.4.10), Java 17, ForgeGradle 6, JUnit 5 (ajouté à `minenorth-portes`), `MineNorth.bank()` (`minenorth-api`), `MapApi` de `minenorth-map` par réflexion.

**Spec:** `docs/superpowers/specs/2026-10-16-agence-immobiliere-design.md` (dans `minenorth-immo`). Les exécutants lisent le plan ET le spec.

## Global Constraints

- Revente : **65 %** du prix d'achat (le code actuel fait 50 %). Arrondi au centime inférieur (choix du plan).
- Limites : **2 maisons** et **2 appartements** par joueur, **2 locaux d'entreprise** par entreprise ; achats et locations en cours comptent ensemble.
- Montants en **centimes** (`long`). Durées en jours, `86_400_000L` ms par jour.
- Location : paiement unique, prolongation = durée de base de l'admin, au prix de base, **ajoutée aux jours restants**. Un bien loué ne se revend pas. Fin de location : accès perdu, affaires laissées sur place, bien de nouveau au catalogue.
- Catégories : `HOUSE`, `APARTMENT`, `BUSINESS`. Bien d'entreprise : seul le **patron** achète, loue, prolonge, revend ; paiement et remboursement sur le **compte entreprise** ; bien propriété de l'entreprise ; **tous les membres** ouvrent les portes (grade minimum `99`).
- Aucun achat, location ni revente depuis la porte : message « rendez-vous à l'agence ».
- Paiement d'abord ; si l'attribution échoue, remboursement (`refund`). Jamais d'argent perdu sans bien, ni de bien sans paiement.
- `minenorth-immo` exige `minenorthportes` et `minenorthapi` ; `minenorth_map` reste optionnel (par réflexion, comme `MapBridge` de `minenorth-police`).
- Textes en français. Style des écrans : `MineNorthStyle` / `MineNorthButton` (copiés par mod, comme dans les autres mods).
- Un bien = toutes les portes de même nom de maison (insensible à la casse) dans la même dimension. Un bien sans nom de maison ne peut pas être mis en vente ou en location.
- Commande de test `/agence` ouverte à tous ; elle appelle `AgencyMenu.open(ServerPlayer)` que le futur PNJ réutilisera.
- Dépôts : les tâches 1 à 8 se commitent dans `minenorth-portes` (branche du worktree `claude/practical-hofstadter-6729d5`), les tâches 9 à 12 dans `minenorth-immo`.

## Review Focus

1. Deux joueurs cliquent « Acheter » sur le même bien presque en même temps : un seul doit l'obtenir, l'autre reçoit « plus disponible » et n'est pas débité.
2. Un patron sans compte entreprise ou avec solde insuffisant : achat refusé avec message, aucun changement.
3. Prolonger une location déjà expirée ou un bien dont la fiche a changé de prix entre l'affichage et le clic : refus ou prix du moment, jamais un prix périmé.
4. Image d'aperçu trop grande, trop nombreuse ou paquet tronqué : refus côté serveur sans planter ni remplir le disque.
5. Entreprise supprimée, ou joueur qui n'est plus patron, alors que le bien est encore à elle : il ne peut plus agir, le bien reste cohérent.

---

## File Structure

`minenorth-portes/src/main/java/fr/minenorth/portes/agency/` (nouveau) :
- `PropertyCategory.java` — enum des catégories.
- `ImmoRules.java` — règles pures (65 %, limites, prolongation).
- `PropertyInfoData.java` — `SavedData` : catégorie, meublé, description, images par maison.
- `PropertyImages.java` — stockage disque des captures + paquets de capture admin.
- `AgencyService.java` — catalogue, achat, location, prolongation, revente, « mes biens ».
- Modifiés : `door/OwnerDoorData.java`, `door/OwnerDoorManager.java`, `door/EntrepriseCompat.java`, `door/WorldGuardBridge.java`, `network/ModNetwork.java`, `client/DoorPanelScreen.java`, `build.gradle`.
- Tests : `src/test/java/fr/minenorth/portes/agency/ImmoRulesTest.java`.

`minenorth-immo/` (nouveau mod, package `fr.minenorth.immo`) : `MineNorthImmo.java`, `AgencyMenu.java`, `AgencyCommand.java`, `network/ImmoNetwork.java` (+ paquets), `client/CatalogScreen.java`, `client/PropertyScreen.java`, `client/MyPropertiesScreen.java`, `server/MapBridge.java` (la carte se pilote côté serveur).

---

### Task 1: Règles pures et tests

**Files:**
- Create: `minenorth-portes/src/main/java/fr/minenorth/portes/agency/PropertyCategory.java`, `.../agency/ImmoRules.java`
- Test: `minenorth-portes/src/test/java/fr/minenorth/portes/agency/ImmoRulesTest.java`
- Modify: `minenorth-portes/build.gradle` (JUnit 5 : `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `test { useJUnitPlatform() }`)

**Interfaces:**
- Produces: `enum PropertyCategory { HOUSE, APARTMENT, BUSINESS; static PropertyCategory from(String s) }` (défaut `HOUSE`) ; `ImmoRules.RESALE_PERCENT = 65` ; `static long resaleCents(long purchaseCents)` ; `static int limit(PropertyCategory c)` (2/2/2) ; `static boolean canAcquire(PropertyCategory c, int heldNow)` ; `static long extendedExpiryMs(long nowMs, long currentExpiryMs, int days)`.

- [ ] **Step 1: Write the failing tests** dans `ImmoRulesTest` : `resaleCents(4_000_000L) == 2_600_000L` ; `resaleCents(1) == 0` ; `canAcquire(HOUSE,1)` vrai, `canAcquire(HOUSE,2)` faux, idem `APARTMENT` et `BUSINESS` ; `extendedExpiryMs(1_000, 5_000, 1) == 5_000 + 86_400_000L` ; `extendedExpiryMs(10_000, 5_000, 2) == 10_000 + 172_800_000L` (expiré : repart de maintenant) ; `PropertyCategory.from("bogus") == HOUSE`.
- [ ] **Step 2: Run** `./gradlew test` dans `minenorth-portes`. Expected: échec de compilation (classes absentes).
- [ ] **Step 3: Implement** les deux classes avec les signatures ci-dessus.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: Commit** `git add -A minenorth-portes && git commit -m "feat(agency): regles pures (65 %, limites, prolongation)"`.

### Task 2: Informations du bien (catégorie, meublé, description, images)

**Files:**
- Create: `.../agency/PropertyInfoData.java`

**Interfaces:**
- Produces: `PropertyInfoData extends SavedData`, `static PropertyInfoData get(ServerLevel any)` (stocké sur l'overworld, nom `minenorthportes_property_info`, même schéma que `OwnerDoorData.get`) ; `record PropertyInfo(PropertyCategory category, boolean furnished, String description, List<String> imageIds)` ; `PropertyInfo info(OwnerDoorData doors, String dimension, String houseName)` (clé = dimension + `|` + nom en minuscules ; sans fiche enregistrée : catégorie `BUSINESS` si une porte de la maison a un `permission` lié à une entreprise, sinon `HOUSE` ; meublé faux ; description vide ; aucune image) ; `void set(String dimension, String houseName, PropertyCategory c, boolean furnished, String description)` (description tronquée à 200 caractères) ; `void addImage(String dimension, String houseName, String imageId)` ; `List<String> clearImages(String dimension, String houseName)` (renvoie les ids retirés).

- [ ] **Step 1: Implement** la classe et la sauvegarde NBT.
- [ ] **Step 2: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(agency): informations du bien`.

### Task 3: Propriété d'entreprise et prolongation dans les données des portes

**Files:**
- Modify: `door/OwnerDoorData.java`, `door/EntrepriseCompat.java`

**Interfaces:**
- Produces dans `OwnerDoorData` : `DoorListing` reçoit un champ `int renterCompany` (0 = aucun ; ajouter à l'NBT sous `RenterCompany` ; garder les anciens constructeurs avec 0) ; `void transferHouseToCompany(DoorKey key, int companyId, String companyName, long purchasePrice)` (portes de la maison : type `ENTREPRISE`, `permission = EntrepriseCompat.permission(companyId, 99)`, `ownerName = companyName`, owner null, listings retirés) ; `void rentToCompany(ServerLevel level, BlockPos pos, UUID boss, int companyId, long expiryMs)` (même effet que `rent` + portes en `ENTREPRISE` pendant le bail, `renterCompany = companyId`) ; `void extendRental(DoorKey key, long newExpiryMs)` ; `returnHouseToMarket` et `expireRentals` sont étendus pour remettre en `PERSONAL` sans propriétaire aussi les portes `ENTREPRISE` d'une maison (fin de bail ou revente d'entreprise).
- Produces dans `EntrepriseCompat` (réflexion, comme les autres) : `UUID ownerUuid(MinecraftServer s, int companyId)` et `UUID accountId(MinecraftServer s, int companyId)`. Lire `minenorth-entreprises/.../api/EntrepriseApi.java` et `CompanyAccounts.java` : si la méthode n'existe pas côté `EntrepriseApi`, l'ajouter là (un `public static` qui délègue à `CompanyAccounts`/`EntrepriseData`).

- [ ] **Step 1: Implement** les changements ci-dessus, NBT rétro-compatible (champ absent = 0).
- [ ] **Step 2: Build** `./gradlew build` dans `minenorth-portes`, et dans `minenorth-entreprises` si modifié. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(portes): proprietaire entreprise, prolongation`.

### Task 4: AgencyService (catalogue, achat, location, prolongation, revente)

**Files:**
- Create: `.../agency/AgencyService.java`
- Modify: `door/OwnerDoorManager.java` (les méthodes privées `buy`, `rent`, `resell`, `pay` sont remplacées par `AgencyService` ; `ImmoRules.resaleCents` remplace `originalPrice / 2`)

**Interfaces:**
- Consumes: `ImmoRules`, `PropertyInfoData`, `OwnerDoorData` (Task 3), `EntrepriseCompat`, `MineNorth.bank()` (`check`, `charge`, `transfer`, `refund`, `payFromAccount`).
- Produces :
  - `record AgencyResult(boolean ok, String message)`
  - `record PropertyView(String dimension, String houseName, PropertyCategory category, OwnerDoorData.ListingMode mode, long priceCents, int days, boolean furnished, String description, int imageCount, int x, int y, int z)` (x,y,z = première porte de la maison, triée par position)
  - `record OwnedView(PropertyView view, boolean rented, long expiryMs, long purchaseCents, int companyId, String companyName)`
  - `static List<PropertyView> catalog(MinecraftServer s)` — maisons dont la fiche est active (vente, ou location sans locataire en cours), une entrée par maison
  - `static List<OwnedView> myProperties(ServerPlayer p)` — biens personnels du joueur (achetés ou loués) + ceux des entreprises dont il est patron
  - `static AgencyResult buy(ServerPlayer p, String dimension, String houseName, int companyId)` ; `rent(...)` même signature ; `extend(ServerPlayer p, String dimension, String houseName)` ; `resell(ServerPlayer p, String dimension, String houseName)`
  - `companyId` ignoré (0) pour `HOUSE`/`APARTMENT` ; obligatoire et vérifié (`EntrepriseCompat.isOwner`) pour `BUSINESS`, qui se paie avec `payFromAccount(server, EntrepriseCompat.accountId(...), cents, "portes:achat", p.getUUID())`.
  - Toutes les méthodes relisent le prix et la disponibilité au moment de l'appel (jamais ceux affichés). Vérifier d'abord distance non requise (l'agence n'est pas près du bien), limite (`ImmoRules.canAcquire` avec le nombre tenu), disponibilité, puis payer, puis attribuer ; si l'attribution échoue, `refund`. Résultats avec messages français (« Plus disponible », « Limite atteinte : 2 maisons », « Paiement refusé : … »).
  - `static int heldCount(MinecraftServer s, UUID player, PropertyCategory c)` et `static int heldCountCompany(MinecraftServer s, int companyId)` (achats + locations en cours).
  - Revente : refusée si le bien est loué ; rembourse `ImmoRules.resaleCents(purchase)` sur le compte personnel ou entreprise, `returnHouseToMarket(key, purchase)`, `WorldGuardBridge.release` comme avant.

- [ ] **Step 1: Implement** `AgencyService`. Un seul `synchronized` (ou le thread serveur, tout est appelé depuis lui) suffit pour la concurrence : relire l'état juste avant de payer.
- [ ] **Step 2: Remove** de `OwnerDoorManager` les anciens `buy` / `rent` / `resell` / `pay` devenus inutilisés (leurs appelants sont retirés en Task 5).
- [ ] **Step 3: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Verify** sur serveur de test (`./gradlew runServer`) : en console, bien en vente à 100 € → `AgencyService.buy` via la commande de la Task 10 ; vérifier plus tard en Task 12. (Pas de test automatique : classes Minecraft.)
- [ ] **Step 5: Commit** `feat(agency): service d'agence (catalogue, achat, location, prolongation, revente 65 %)`.

### Task 5: La porte renvoie vers l'agence

**Files:**
- Modify: `door/OwnerDoorManager.java` (`handleAction` cas 10, 11, 17 ; cas 7, 8, 18 : refuser un nom de maison vide), `client/DoorPanelScreen.java` (`buildListing`)

- [ ] **Step 1: Implement** : les cas 10, 11, 17 envoient seulement `§cRendez-vous à l'agence immobilière pour acheter, louer ou revendre ce bien.` et ne font rien d'autre. Les cas 7, 8, 18 refusent un nom de maison vide avec un message.
- [ ] **Step 2: Implement** `buildListing` : le bouton affiche « RENDEZ-VOUS À L'AGENCE » et est désactivé ; supprimer les envois des actions 10/11. Le texte du panneau reste informatif (prix, durée).
- [ ] **Step 3: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat(portes): achat et location uniquement via l'agence`.

### Task 6: Configuration du bien dans le panneau admin

**Files:**
- Modify: `network/ModNetwork.java` (`DoorPanelPacket` : ajouter à la fin `String category, boolean furnished, String description, int imageCount`, dans le record, `encode`, `decode` et `sendDoorPanel`), `door/OwnerDoorManager.java` (nouveau cas **22** dans `handleAction`), `client/DoorPanelScreen.java`

**Interfaces:**
- Produces : action réseau `22`, `text = "<CATEGORY>|<furnished 0/1>"`, `extra = description` ; OP uniquement ; appelle `PropertyInfoData.set(...)` pour la maison de la porte, écrit la sauvegarde, renvoie `sendAdminPanel`.
- Un bien dont la catégorie est `BUSINESS` n'a pas besoin d'être attribué à une entreprise avant : c'est l'achat qui l'attribue.

- [ ] **Step 1: Implement** le serveur (cas 22, paquet étendu avec les valeurs de `PropertyInfoData.info`).
- [ ] **Step 2: Implement** dans `DoorPanelScreen`, bloc `buildListingSetup` et panneau admin : trois boutons de catégorie (MAISON / APPARTEMENT / ENTREPRISE), une case « Meublé », un champ « Description », bouton « ENREGISTRER » qui envoie l'action 22. Afficher le nombre d'images (voir Task 7).
- [ ] **Step 3: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat(portes): categorie, meuble et description dans le panneau admin`.

### Task 7: Captures d'aperçu

**Files:**
- Create: `.../agency/PropertyImages.java`
- Modify: `network/ModNetwork.java` (deux paquets : `ImageUploadPacket(String imageId, int index, int total, byte[] chunk, boolean reset)` client→serveur, `ImageClearPacket()`), `client/DoorPanelScreen.java` (boutons « PRENDRE UNE CAPTURE » et « EFFACER LES CAPTURES »), `door/OwnerDoorManager.java`

**Interfaces:**
- Produces : `PropertyImages.DIR` = `<monde>/minenorth_immo/images/` ; `static boolean save(MinecraftServer s, String imageId, byte[] jpeg)` (refuse > **150 000 octets** ou non-JPEG) ; `static byte[] load(MinecraftServer s, String imageId)` (null si absent) ; `static final int MAX_PER_PROPERTY = 4`.
- Côté client : capture de la vue du joueur (`Screenshot` / cible principale), réduite à **640×360**, encodée en JPEG, découpée en morceaux de **30 000 octets** maximum par paquet (limite du canal). Fermer le panneau avant la capture pour qu'il ne soit pas dans l'image.
- Côté serveur : OP uniquement, pour la maison de la porte du contexte ; refuse au-delà de `MAX_PER_PROPERTY` ; assemble les morceaux, vérifie la taille cumulée avant de les accumuler (rejette en cours de route), puis `PropertyImages.save` et `PropertyInfoData.addImage`. « Effacer » supprime les fichiers via `clearImages`.

- [ ] **Step 1: Implement** serveur et paquets.
- [ ] **Step 2: Implement** client (capture, réduction, envoi) et boutons du panneau.
- [ ] **Step 3: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Verify** à la main : prendre 5 captures, la 5e est refusée ; un fichier d'environ 100 Ko apparaît dans `minenorth_immo/images/`.
- [ ] **Step 5: Commit** `feat(portes): captures d'apercu du bien`.

### Task 8: WorldGuard pour les biens d'entreprise

**Files:**
- Modify: `door/WorldGuardBridge.java`

- [ ] **Step 1: Implement** : pour une maison dont les portes sont de type `ENTREPRISE` avec un nom de maison, le membre de la région est le patron (`EntrepriseCompat.ownerUuid`). À la revente, fin de bail ou entreprise supprimée, le retirer comme pour un propriétaire personnel (`release`). `/portewg resync` doit aussi couvrir ce cas.
- [ ] **Step 2: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(portes): WorldGuard pour les locaux d'entreprise`.

### Task 9: Squelette du mod `minenorth-immo` et commande `/agence`

**Files:**
- Create dans `minenorth-immo/` : `build.gradle` (copie de celui de `minenorth-portes`, `archivesName = 'minenorth-immo'`, `mods { minenorthimmo { source sourceSets.main } }`, dépendances `fg.deobf("blank:minenorth_api:1.0.0")` et `fg.deobf("blank:minenorth-portes:1.0.0")` avec `flatDir` vers `../minenorth-api/build/libs` et `../minenorth-portes/build/libs`), `settings.gradle`, `gradle.properties`, `gradle/` et `gradlew*` copiés d'un autre mod, `src/main/resources/META-INF/mods.toml` (modId `minenorthimmo`, dépendances obligatoires forge, minecraft, `minenorthportes`, `minenorthapi`, `minenorth_eurobank`), `src/main/java/fr/minenorth/immo/{MineNorthImmo,AgencyMenu,AgencyCommand}.java`, `network/ImmoNetwork.java`, `server/MapBridge.java` (copie adaptée de `MapBridge` de `minenorth-police`).

**Interfaces:**
- Produces : `AgencyMenu.open(ServerPlayer p)` (envoie `OpenAgencyPacket` avec `AgencyService.catalog`) ; commande `/agence` (aucun niveau de permission requis) qui appelle `AgencyMenu.open`.
- Prérequis de build : construire d'abord `minenorth-api` puis `minenorth-portes` (`./gradlew build`), qui produisent les `.jar` dans `build/libs`.

- [ ] **Step 1: Create** les fichiers ci-dessus ; la classe principale enregistre le réseau et la commande.
- [ ] **Step 2: Build** `./gradlew build` dans `minenorth-immo`. Expected: BUILD SUCCESSFUL et `build/libs/minenorth-immo-1.0.0.jar`.
- [ ] **Step 3: Verify** : sur un serveur de test avec les mods, `/agence` ne plante pas (l'écran arrive en Task 10).
- [ ] **Step 4: Commit** `feat(immo): squelette du mod et commande /agence`.

### Task 10: Catalogue (grille de cartes, filtres)

**Files:**
- Create: `client/CatalogScreen.java` ; paquets dans `network/ImmoNetwork.java`
- Modify: `AgencyMenu.java`

**Interfaces:**
- Consumes: `AgencyService.PropertyView`.
- Produces : `OpenAgencyPacket(List<PropertyView> catalog)` (serveur→client, sérialisation champ par champ) ; `CatalogScreen(List<PropertyView>)` : grille de cartes (nom, prix, type, catégorie, vignette si disponible), onglets « Catalogue » / « Mes biens », filtres : achat/location, catégorie (Maison / Appartement / Entreprise), prix maximum, meublé ; clic sur une carte → `PropertyScreen` (Task 11).

- [ ] **Step 1: Implement** paquet et écran. Filtrage côté client.
- [ ] **Step 2: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Verify** : avec 3 biens (maison en vente, appartement en location, local d'entreprise), `/agence` affiche 3 cartes ; chaque filtre en retire ce qu'il faut ; un bien acheté disparaît à la réouverture.
- [ ] **Step 4: Commit** `feat(immo): catalogue de biens`.

### Task 11: Fiche du bien (aperçu, carte, achat/location)

**Files:**
- Create: `client/PropertyScreen.java` ; paquets `RequestImagePacket(dimension, houseName, index)` / `ImagePacket(chunks)`, `AgencyActionPacket(action, dimension, houseName, companyId)` (actions : `BUY`, `RENT`, `EXTEND`, `RESELL`, `SHOW_ON_MAP`) et `ActionResultPacket(boolean ok, String message)`
- Modify: `network/ImmoNetwork.java`

**Interfaces:**
- Consumes: `AgencyService.buy/rent/extend/resell`, `PropertyImages.load`, `MapBridge`.
- Produces : fiche avec images (flèches ◀ ▶, téléchargées à l'ouverture en morceaux de ≤ 30 000 octets, mises en cache côté client), prix, durée, meublé, description, bouton « Voir sur la carte » (serveur : `MapBridge.set(player, "Bien : <nom>", dimension, x, y, z, color, true)` ; bouton masqué ou désactivé si `minenorth_map` est absent), bouton « Acheter » ou « Louer » ; pour un bien `BUSINESS`, liste déroulante des entreprises dont le joueur est patron (`EntrepriseCompat.companies` filtré par `isOwner`) et message clair s'il n'en a aucune. Le résultat serveur s'affiche en message.

- [ ] **Step 1: Implement** paquets et gestion serveur (les actions appellent `AgencyService` et renvoient `ActionResultPacket`, puis un `OpenAgencyPacket` frais).
- [ ] **Step 2: Implement** `PropertyScreen`.
- [ ] **Step 3: Build** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Verify** : acheter une maison à 100 € avec un compte à 200 € → solde 100 €, bien disparu du catalogue ; refus avec 50 € ; « Voir sur la carte » pose un repère guidé.
- [ ] **Step 5: Commit** `feat(immo): fiche du bien, achat, location, carte`.

### Task 12: « Mes biens » et vérification finale

**Files:**
- Create: `client/MyPropertiesScreen.java`
- Modify: `AgencyMenu.java`, `network/ImmoNetwork.java` (`OpenMyPropertiesPacket(List<OwnedView>)`)

**Interfaces:**
- Consumes: `AgencyService.myProperties`, `AgencyService.extend`, `AgencyService.resell`.
- Produces : liste des biens du joueur (et de ses entreprises s'il est patron) : achetés avec bouton « Revendre » (affiche le montant à 65 %), loués avec jours restants et bouton « Prolonger » (affiche le prix de base et la durée ajoutée) ; confirmation avant revente.

- [ ] **Step 1: Implement** paquet et écran.
- [ ] **Step 2: Build** les quatre mods : `minenorth-api`, `minenorth-portes`, `minenorth-entreprises` (si modifié), `minenorth-immo`. Expected: BUILD SUCCESSFUL partout.
- [ ] **Step 3: Verify** sur serveur de test, chaque ligne doit réussir :
  - la porte d'un bien en vente ne permet plus d'acheter ni de louer ;
  - revente d'une maison achetée 100 € rend 65 € et remet le bien au catalogue ;
  - location de 3 jours puis « Prolonger » : fin de bail à +6 jours depuis le début ; une location ne propose pas « Revendre » ;
  - 3e maison refusée (limite 2) ; 3e local d'une entreprise refusé ;
  - un employé non patron ne peut pas acheter un local, mais ouvre ses portes ;
  - un local d'entreprise achète et rembourse sur le compte entreprise ;
  - bail expiré (régler le temps de fin à la main dans la sauvegarde) : portes fermées au locataire, bien revenu au catalogue.
- [ ] **Step 4: Commit** `feat(immo): mes biens, prolongation, revente`.

---

## Self-Review

- **Couverture du spec :** catalogue + filtres (T10) ; fiche avec aperçu, carte, meublé, configuration (T6, T7, T11) ; « Mes biens » avec revente 65 % et prolongation (T4, T12) ; entreprises : patron seul, compte entreprise, propriété entreprise, accès pour tous (T3, T4, T8) ; limites 2/2/2 (T1, T4) ; porte sans achat (T5) ; commande de test réutilisable par le PNJ (T9) ; migration des biens existants : la catégorie par défaut est dans `PropertyInfoData.info` (T2).
- **Cohérence des types :** `PropertyView`, `OwnedView`, `AgencyResult` définis en T4 et utilisés en T10 à T12 ; `PropertyCategory` en T1 ; `renterCompany` et `transferHouseToCompany` en T3 utilisés en T4.
- **Hors périmètre respecté :** pas de PNJ, pas de gestion des affaires laissées, pas de loyer automatique.
- **Proportion :** plan court, aucun corps de fonction ; seuls les noms, signatures, valeurs du spec et vérifications sont fixés.
- **Limite connue :** seule `ImmoRules` est testée automatiquement ; les classes Minecraft n'ont pas de banc de test dans ces dépôts, d'où les vérifications manuelles chiffrées.
