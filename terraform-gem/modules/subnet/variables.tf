variable "vpc_id" {
  description = "The VPC ID"
  type        = string
}

variable "subnet_cidr" {
  description = "The subnet CIDR block"
  type        = string
}

variable "availability_zone" {
  description = "The availability zone for the subnet"
  type        = string
}

variable "vpc_name" {
  description = "The VPC name (for tagging)"
  type        = string
}

variable "route_table_id" {
  description = "The route table ID to associate with this subnet"
  type        = string
}
