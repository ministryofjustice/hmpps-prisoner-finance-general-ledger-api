package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums

enum class PostingType {
  CR,
  DR,
}

fun PostingType.oppositePostingType(): PostingType {
  if (this == PostingType.CR) {
    return PostingType.DR
  } else {
    return PostingType.CR
  }
}
