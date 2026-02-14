variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "sovereign-vpn-core"
}

variable "region" {
  description = "GCP region for resources"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP zone for the compute instance"
  type        = string
  default     = "us-central1-f"
}
