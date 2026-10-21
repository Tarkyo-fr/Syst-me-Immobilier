# Agence immobilière MineNorth — design

Date : 2026-10-16. Serveur : MineNorth RP (Forge 1.20.1, Java 17, mods séparés `minenorth-*`).

## Intention

Aujourd'hui, un bien (porte) se vend ou se loue en cliquant sur la porte. On veut que **tout achat, location, prolongation et revente passe par une agence immobilière** : le joueur y cherche un bien dans un catalogue, voit où il se trouve et à quoi il ressemble, puis achète ou loue. La porte ne sert plus qu'à porter les réglages du bien (admin) et à renvoyer le joueur vers l'agence.

Pourquoi : donner un vrai lieu et un vrai parcours RP à l'immobilier, trouver un bien sans courir de porte en porte, et centraliser la gestion de ses biens.

## Objectifs

- Un catalogue de biens disponibles, en grille de cartes (photo, nom, prix, type), avec recherche et filtres (achat / location, maison / appartement / local d'entreprise, prix, meublé ou non).
- Une fiche détaillée par bien : aperçu du bâtiment (plusieurs images), prix, achat ou location, durée de location, meublé ou non, configuration, mini-carte, bouton d'action.
- Un onglet « Mes biens » : les biens achetés (bouton Revendre) et les biens loués (jours restants, bouton Prolonger).
- Les locaux d'entreprise : achat et location par le patron uniquement, payés sur le compte de l'entreprise, propriété de l'entreprise.
- Toute la configuration d'un bien se fait dans le panneau admin de la porte.
- Un point d'entrée : pour l'instant une commande de test, plus tard un PNJ « agent immobilier ».

## Hors périmètre

- Le PNJ agent immobilier (viendra après, la commande sert de test).
- Le sort des affaires laissées dans un logement à la fin d'une location : elles restent sur place. À reprendre plus tard.
- Prélèvement automatique de loyer, loyers entre joueurs, enchères, négociation.
- Rendu 3D en direct du bâtiment.

## Ce qui ferait échouer le projet même s'il « marche »

- Pouvoir encore acheter ou louer depuis la porte.
- Un bien qui apparaît dans l'agence alors qu'il n'est plus disponible, ou l'inverse.
- Un joueur qui perd de l'argent sans recevoir le bien, ou garde le bien sans payer.
- Une revente ou une prolongation qui ne crédite ou ne débite pas le bon compte (personnel ou entreprise).

## Ce qui existe déjà (vérifié dans le code)

- `minenorth-portes` : `OwnerDoorData` enregistre pour chaque porte le propriétaire, le type, le nom de maison, le prix d'achat, et une fiche `DoorListing` (mode `SALE` ou `RENTAL`, prix en centimes, nombre de jours, locataire, date de fin). Les portes de même nom de maison forment un seul bien. `OwnerDoorManager` contient `buy`, `rent`, `resell`, `listSale`, `listRental` et l'expiration des locations. Un lien WorldGuard donne les droits de construire au propriétaire et au locataire et les retire à la fin du bail ou à la revente. Le panneau admin est `DoorPanelScreen`. L'échange client/serveur passe par `ModNetwork` (`DoorPanelPacket`, `DoorActionPacket`). Un lien vers les entreprises existe déjà (`EntrepriseCompat`).
- Revente actuelle : 50 % du prix d'achat (`resell`). Le nouveau système doit rembourser **65 %**.
- `minenorth-eurobank` / `minenorth-api` : `MineNorth.bank()` fournit `check`, `charge`, `transfer`, `refund`, `payFromAccount` (compte entreprise). Les montants sont en centimes.
- `minenorth-map` : `MapApi.setMarker(ServerPlayer, name, dim, x, y, z, color, guide)` pose un repère et peut lancer un guidage sur la carte du joueur. Appelé par réflexion (voir `minenorth-police` `MapBridge`) : aucune dépendance de compilation.
- `minenorth-immo` : dossier vide, c'est ici que naît le nouveau mod.

## Décisions de l'utilisateur

**Parcours joueur**
- Il va à l'agence, ouvre le catalogue, filtre, clique sur un bien, voit sa fiche, achète ou loue depuis la fiche.
- Depuis la porte, plus aucun achat ni location : un message dit d'aller à l'agence.

**Location**
- Le joueur paie une fois le prix, pour un nombre de jours défini par l'admin sur la porte.
- Prolongation : bouton dans « Mes biens », sans choix de durée. Elle ajoute la durée de base réglée par l'admin, au prix de base.
- À la fin sans prolongation : le joueur perd l'accès, les affaires restent sur place, le bien redevient disponible dans le catalogue.

**Achat et revente**
- Un bien acheté se revend uniquement à l'agence, à 65 % du prix d'achat.
- Un bien loué ne se revend pas.

**Catégories de bien :** maison, appartement, local d'entreprise (trois catégories, chacune un filtre dans l'agence).

**Configuration (panneau admin de la porte)**
- Tout se règle ici : en vente ou en location, prix, durée de base, meublé ou non, configuration, catégorie (maison, appartement, local d'entreprise), images d'aperçu.

**Aperçu du bâtiment**
- L'admin se place devant le bien et clique sur un bouton du panneau de la porte : une capture de sa vue est prise et enregistrée sur le serveur. Il peut en prendre plusieurs (extérieur, intérieur). Le joueur les voit sur la fiche.

**Entreprises**
- Un bien de catégorie « entreprise » se retrouve dans la catégorie Entreprise de l'agence.
- Seul le patron de l'entreprise peut acheter, louer, prolonger ou revendre. Le paiement et le remboursement se font sur le compte de l'entreprise. Le bien appartient à l'entreprise.

**Point d'entrée**
- Première version : une commande (pour tester). Ensuite : un PNJ agent immobilier.

## Choix du rédacteur (à valider par l'utilisateur)

- Architecture : un nouveau mod `minenorth-immo` (catalogue, fiche, « Mes biens », commande). Les données et la logique d'achat/location/revente/prolongation restent dans `minenorth-portes`, qui expose des méthodes publiques appelées par `minenorth-immo`. Le prix de revente passe de 50 % à 65 % dans `minenorth-portes`.
- Catégorie du bien : un nouveau champ (maison / appartement / entreprise) enregistré avec la fiche du bien dans `minenorth-portes`, réglé dans le panneau admin.
- Un bien n'apparaît au catalogue que s'il a une fiche active (en vente, ou en location sans locataire en cours). Un bien vendu ou loué disparaît du catalogue.
- Le catalogue regroupe les portes du même nom de maison en un seul bien.
- Les prolongations s'ajoutent aux jours restants (prolonger tôt ne fait rien perdre).
- Captures d'aperçu : stockées sur le serveur, réduites et limitées en nombre et en taille par bien, envoyées au client à l'ouverture de la fiche.
- Bouton « Voir sur la carte » : utilise `MapApi.setMarker` avec guidage, et reste inactif si `minenorth-map` est absent.
- La commande de test est ouverte aux joueurs pendant la phase de test.

## Précisions ajoutées après relecture

**Déjà tranché par le code ou la conversation**
- Un bien d'entreprise utilise ce qui existe : porte de type `ENTREPRISE` dont le champ « permission » vaut `entreprise:<id>:<gradeMin>` (`EntrepriseCompat`). Le propriétaire est donc l'entreprise (son id), pas le patron. Aujourd'hui seules les portes `PERSONAL` se vendent : le nouveau système doit aussi pouvoir vendre et louer un bien d'entreprise. Le lien WorldGuard ne gère que les portes personnelles : à étendre pour qu'une entreprise propriétaire ou locataire donne les droits de construire au patron.
- Les actions de la porte « acheter » (10), « louer » (11) et « revendre » (17) dans `OwnerDoorManager.handleAction` sont retirées du clic sur la porte : elles renvoient le message « rendez-vous à l'agence ». La logique `buy` / `rent` / `resell` reste dans `minenorth-portes`, appelable uniquement par l'agence. Les actions admin de mise en vente et en location (7, 8) restent, car c'est la configuration du bien.
- Une porte sans propriétaire (`owner == null`, bien géré par les admins) existe déjà : c'est le cas normal d'un bien neuf. L'argent d'une vente va alors au trésor public.
- La catégorie du bien décide du payeur : un bien « entreprise » se paie toujours par le compte entreprise du joueur, une maison ou un appartement toujours par son compte personnel. Le joueur ne choisit pas.

**Mes choix (à valider)**
- Un joueur patron de plusieurs entreprises choisit pour laquelle il achète (liste déroulante à l'achat d'un local d'entreprise).
- « Mes biens » affiche les biens personnels du joueur et, s'il est patron, ceux de ses entreprises.
- Droits dans un local d'entreprise : **tous les membres de l'entreprise** peuvent ouvrir les portes (grade minimum « tous les employés », valeur 99 du réglage existant). Seul le patron achète, loue, prolonge et revend.
- Entreprise supprimée ou patron remplacé : le bien reste attaché à l'entreprise, donc le nouveau patron en hérite. Si l'entreprise disparaît, le bien repasse au catalogue, sans remboursement.
- Biens déjà en vente ou possédés avant ce changement : catégorie par défaut « maison », ou « entreprise » si la porte est déjà reliée à une entreprise. Un bien sans image s'affiche avec une image par défaut.
- Un bien est géré au niveau de la maison : toutes les portes du même nom partagent la même fiche (c'est déjà le cas dans `setSale` / `setRental`). En cas d'incohérence entre portes, la première porte (triée par position) fait foi.
- Limites (décidées par l'utilisateur) : un joueur peut avoir au plus **2 maisons** et **2 appartements** ; une entreprise au plus **2 locaux d'entreprise**. Interprétation à valider : la limite est comptée par catégorie, achats et locations en cours confondus. Un achat ou une location au-delà de la limite est refusé avec un message.
- `minenorth-immo` exige `minenorth-portes` et `minenorth-api`. Sans banque disponible, tout achat est refusé avec un message. L'achat et la location sont atomiques : le paiement est fait d'abord, et si l'attribution du bien échoue, le joueur est remboursé (`refund`).
- La commande de test ouvre l'agence partout. Elle appelle une méthode générique « ouvrir l'agence pour ce joueur » que le PNJ réutilisera plus tard, et c'est le PNJ qui vérifiera alors la distance.

## Laissé au bâtisseur

- Le découpage exact des classes et des paquets réseau, la mise en page précise des écrans (en suivant le style `MineNorthStyle` / `MineNorthButton` des autres mods), le format de stockage des images, la gestion de plusieurs joueurs qui achètent le même bien en même temps, les textes des messages.
